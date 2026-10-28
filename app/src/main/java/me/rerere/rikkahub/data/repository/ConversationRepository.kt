package me.rerere.rikkahub.data.repository

import android.database.sqlite.SQLiteBlobTooBigException
import android.util.LruCache
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.FavoriteDAO
import me.rerere.rikkahub.data.db.dao.MessageNodeDAO
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.MessageNodeEntity
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.db.fts.MessageSearchSort
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConversationRepository(
    private val conversationDAO: ConversationDAO,
    private val messageNodeDAO: MessageNodeDAO,
    private val favoriteDAO: FavoriteDAO,
    private val database: AppDatabase,
    private val filesManager: FilesManager,
    private val messageFtsManager: MessageFtsManager,
) {
    companion object {
        private const val PAGE_SIZE = 20
        private const val INITIAL_LOAD_SIZE = 40

        /**
         * 已解码会话的缓存条数上限。
         *
         * 一个会话的 `Conversation` 含全部消息节点，体积可能很大（实测有 1024 条消息的会话）。
         * 所以按**条数**限制而不是字节，并配合 [MAX_CACHED_NODES] 再兜一层：
         * 节点数超限的会话不进缓存 —— 宁可让它每次现读，也不能把内存吃满。
         */
        private const val MAX_CACHED_CONVERSATIONS = 6

        /** 单会话缓存门槛：节点数超过这个值就不缓存（防止超大会话占满内存）。 */
        private const val MAX_CACHED_NODES = 2000

        /** 启动预载：最多完整解码几个会话。 */
        private const val PRELOAD_MAX_CONVERSATIONS = 4

        /** 启动预载：累计消息节点总预算（超过就停，防止 OOM）。 */
        private const val PRELOAD_NODE_BUDGET = 3000
    }

    /**
     * 「已解码会话」缓存。
     *
     * ## 为什么需要它
     *
     * 打开会话的真实开销不是 SQL，而是 `loadMessageNodes` 里对每条 node 的
     * `JsonInstant.decodeFromString<List<UIMessage>>(...)` —— 消息体是一大坨 JSON，
     * 解码是纯 CPU 操作。启动页即使把 SQL 预热了，点开时**仍然要再解码一遍**。
     *
     * 这份缓存让启动页预载的解码结果能被打开会话时直接复用。
     *
     * ## 一致性
     *
     * 任何写操作都必须 [invalidateDecodedCache]，否则会读到旧数据。
     * 已在 insert/update/delete/saveMessageNodes/各 update* 中逐一调用。
     */
    private val decodedCache = object : LruCache<Uuid, Conversation>(MAX_CACHED_CONVERSATIONS) {}

    private fun invalidateDecodedCache(id: Uuid) {
        decodedCache.remove(id)
    }

    suspend fun hasFileReference(fileUrl: String): Boolean =
        messageNodeDAO.hasFileReference(JsonInstant.encodeToString(fileUrl))

    suspend fun getRecentConversations(assistantId: Uuid, limit: Int = 10): List<Conversation> {
        return conversationDAO.getRecentConversationsOfAssistant(
            assistantId = assistantId.toString(),
            limit = limit
        ).map { entity ->
            val nodes = loadMessageNodes(entity.id)
            conversationEntityToConversation(entity, nodes)
        }
    }

    fun getConversationsOfAssistant(assistantId: Uuid): Flow<List<Conversation>> {
        return conversationDAO
            .getConversationsOfAssistant(assistantId.toString())
            .map { flow ->
                flow.map { entity ->
                    // 列表视图不需要完整的 nodes，使用空列表
                    conversationEntityToConversation(entity, emptyList())
                }
            }
    }

    suspend fun getTitlesOfAssistant(assistantId: Uuid): Set<String> =
        conversationDAO.getTitlesOfAssistant(assistantId.toString()).toSet()

    fun getConversationsOfAssistantPaging(assistantId: Uuid): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { conversationDAO.getConversationsOfAssistantPaging(assistantId.toString()) }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            conversationSummaryToConversation(entity)
        }
    }

    fun getUnfiledConversationsOfAssistantPaging(
        assistantId: Uuid,
        sortOrder: ConversationSortOrder = ConversationSortOrder.UPDATE_TIME,
    ): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = {
            conversationDAO.getUnfiledConversationsOfAssistantPaging(
                assistantId = assistantId.toString(),
                sortByCreateTime = sortOrder == ConversationSortOrder.CREATE_TIME,
            )
        }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            conversationSummaryToConversation(entity)
        }
    }

    fun getConversationsOfFolderPaging(
        folderId: Uuid,
        sortOrder: ConversationSortOrder = ConversationSortOrder.UPDATE_TIME,
    ): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = {
            conversationDAO.getConversationsOfFolderPaging(
                folderId = folderId.toString(),
                sortByCreateTime = sortOrder == ConversationSortOrder.CREATE_TIME,
            )
        }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            conversationSummaryToConversation(entity)
        }
    }

    suspend fun getConversationsOfAssistantPage(
        assistantId: Uuid,
        offset: Int,
        limit: Int,
    ): ConversationPageResult {
        val pagingSource = conversationDAO.getConversationsOfAssistantPaging(assistantId.toString())
        return try {
            when (
                val result = pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = if (offset == 0) null else offset,
                        loadSize = limit,
                        placeholdersEnabled = false
                    )
                )
            ) {
                is PagingSource.LoadResult.Page -> ConversationPageResult(
                    items = result.data.map { entity ->
                        conversationSummaryToConversation(entity)
                    },
                    nextOffset = result.nextKey
                )

                is PagingSource.LoadResult.Error -> throw result.throwable
                is PagingSource.LoadResult.Invalid -> ConversationPageResult(emptyList(), null)
            }
        } finally {
            pagingSource.invalidate()
        }
    }

    /**
     * 启动页预载：当前助手「最近一周」的会话。
     *
     * ## 分两步，作用不同
     *
     * **第一步 · 列表元数据**（轻量列，全量）
     * 与主页 Paging 首次查询命中同一批行，把 SQLite 页缓存和实体映射路径焐热。
     *
     * **第二步 · 会话内容解码**（重活，按预算截断）
     * `loadMessageNodes` 里逐条 `JsonInstant.decodeFromString<List<UIMessage>>` 才是
     * 「点开会话卡一下」的真凶 —— 纯 CPU 解码，SQL 预热救不了。
     * 这一步把最近若干会话**完整解码**并放进 [decodedCache]，
     * 于是点开时 `getConversationById` 直接命中缓存，不再解码。
     *
     * ## 为什么必须截断
     *
     * 一个会话可能上千条消息，把一周内所有会话全部解码进内存会直接 OOM。
     * 因此按「最多 N 个会话 / 累计不超过 M 个消息节点」双预算截断，
     * 超预算的会话跳过 —— 它们点开时走正常路径（仍比改之前快，因为不在主线程解码）。
     *
     * @param sinceEpochMillis 起始时间（含），与 `updateAt` 同为毫秒时间戳
     * @return 实际解码进缓存的会话数（供日志/诊断）
     */
    suspend fun preloadRecentConversations(
        assistantId: Uuid,
        sinceEpochMillis: Long,
    ): Int {
        val rows = conversationDAO.getUnfiledConversationsSince(
            assistantId = assistantId.toString(),
            since = sinceEpochMillis,
        )
        // —— 第一步：预热列表元数据转换路径 ——
        runCatching { rows.forEach { conversationSummaryToConversation(it) } }

        // —— 第二步：按预算解码会话内容进缓存 ——
        var decoded = 0
        var nodeBudget = PRELOAD_NODE_BUDGET
        for (row in rows) {
            if (decoded >= PRELOAD_MAX_CONVERSATIONS || nodeBudget <= 0) break
            val id = runCatching { Uuid.parse(row.id) }.getOrNull() ?: continue
            if (decodedCache.get(id) != null) continue

            val conversation = runCatching { getConversationById(id) }.getOrNull() ?: continue
            nodeBudget -= conversation.messageNodes.size
            // 刚好超预算的那个会话不进缓存（getConversationById 内部按 MAX_CACHED_NODES 判断，
            // 这里再按累计预算把已放入的移除，避免累积占用）
            if (nodeBudget < 0) {
                invalidateDecodedCache(id)
                break
            }
            decoded++
        }
        return decoded
    }

    suspend fun searchConversationsOfAssistantPage(
        assistantId: Uuid,
        titleKeyword: String,
        offset: Int,
        limit: Int,
    ): ConversationPageResult {
        val pagingSource = conversationDAO.searchConversationsOfAssistantPaging(
            assistantId = assistantId.toString(),
            searchText = titleKeyword
        )
        return try {
            when (
                val result = pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = if (offset == 0) null else offset,
                        loadSize = limit,
                        placeholdersEnabled = false
                    )
                )
            ) {
                is PagingSource.LoadResult.Page -> ConversationPageResult(
                    items = result.data.map { entity ->
                        conversationSummaryToConversation(entity)
                    },
                    nextOffset = result.nextKey
                )

                is PagingSource.LoadResult.Error -> throw result.throwable
                is PagingSource.LoadResult.Invalid -> ConversationPageResult(emptyList(), null)
            }
        } finally {
            pagingSource.invalidate()
        }
    }

    suspend fun getUnfiledConversationsOfAssistantPage(
        assistantId: Uuid,
        offset: Int,
        limit: Int,
    ): ConversationPageResult = loadConversationPage(
        conversationDAO.getUnfiledConversationsOfAssistantPaging(assistantId.toString(), sortByCreateTime = false),
        offset,
        limit,
    )

    suspend fun getConversationsOfFolderPage(
        folderId: Uuid,
        offset: Int,
        limit: Int,
    ): ConversationPageResult = loadConversationPage(
        conversationDAO.getConversationsOfFolderPaging(folderId.toString(), sortByCreateTime = false),
        offset,
        limit,
    )

    private suspend fun loadConversationPage(
        pagingSource: PagingSource<Int, LightConversationEntity>,
        offset: Int,
        limit: Int,
    ): ConversationPageResult {
        return try {
            when (
                val result = pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = if (offset == 0) null else offset,
                        loadSize = limit,
                        placeholdersEnabled = false
                    )
                )
            ) {
                is PagingSource.LoadResult.Page -> ConversationPageResult(
                    items = result.data.map { entity ->
                        conversationSummaryToConversation(entity)
                    },
                    nextOffset = result.nextKey
                )

                is PagingSource.LoadResult.Error -> throw result.throwable
                is PagingSource.LoadResult.Invalid -> ConversationPageResult(emptyList(), null)
            }
        } finally {
            pagingSource.invalidate()
        }
    }

    fun searchConversations(titleKeyword: String): Flow<List<Conversation>> {
        return conversationDAO
            .searchConversations(titleKeyword)
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity, emptyList())
                }
            }
    }

    fun searchConversationsPaging(titleKeyword: String): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { conversationDAO.searchConversationsPaging(titleKeyword) }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            conversationSummaryToConversation(entity)
        }
    }

    fun searchConversationsOfAssistant(assistantId: Uuid, titleKeyword: String): Flow<List<Conversation>> {
        return conversationDAO
            .searchConversationsOfAssistant(assistantId.toString(), titleKeyword)
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity, emptyList())
                }
            }
    }

    fun searchConversationsOfAssistantPaging(assistantId: Uuid, titleKeyword: String): Flow<PagingData<Conversation>> =
        Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                conversationDAO.searchConversationsOfAssistantPaging(
                    assistantId.toString(),
                    titleKeyword
                )
            }
        ).flow.map { pagingData ->
            pagingData.map { entity ->
                conversationSummaryToConversation(entity)
            }
        }

    suspend fun getConversationById(uuid: Uuid): Conversation? {
        // 命中启动页预载的解码结果 → 直接返回，不再解码
        decodedCache.get(uuid)?.let { return it }

        // ★ 整段显式切到 IO。
        //   Room 的 suspend DAO 与 withTransaction 本身会切线程，但
        //   `conversationEntityToConversation`（内有多处 JsonInstant.decodeFromString）
        //   是**在调用方上下文**里执行的。调用方 ChatVM 用的是 viewModelScope
        //   = Dispatchers.Main.immediate，于是这几处 JSON 解码原本压在主线程上，
        //   直接把页面转场的动画帧吃掉（用户反馈的「卡住约 100ms」）。
        val conversation = withContext(Dispatchers.IO) {
            val entity = conversationDAO.getConversationById(uuid.toString()) ?: return@withContext null
            val nodes = loadMessageNodes(entity.id)
            conversationEntityToConversation(entity, nodes)
        } ?: return null

        // 超大会话不进缓存：宁可每次现读，也不让 LRU 里塞进一个几百 MB 的对象
        if (conversation.messageNodes.size <= MAX_CACHED_NODES) {
            decodedCache.put(uuid, conversation)
        }
        return conversation
    }

    suspend fun existsConversationById(uuid: Uuid): Boolean {
        return conversationDAO.existsById(uuid.toString())
    }

    suspend fun countConversations(): Int {
        return conversationDAO.countAll()
    }

    suspend fun countConversationsByAssistant(): Map<Uuid, Int> {
        return conversationDAO.countByAssistant().mapNotNull { row ->
            runCatching { Uuid.parse(row.assistantId) }.getOrNull()?.let { it to row.count }
        }.toMap()
    }

    suspend fun insertConversation(conversation: Conversation) {
        invalidateDecodedCache(conversation.id)
        database.withTransaction {
            conversationDAO.insert(
                conversationToConversationEntity(conversation)
            )
            saveMessageNodes(conversation.id.toString(), conversation.messageNodes)
        }
        messageFtsManager.indexConversation(conversation)
    }

    suspend fun updateConversation(conversation: Conversation) {
        invalidateDecodedCache(conversation.id)
        database.withTransaction {
            conversationDAO.update(
                conversationToConversationEntity(conversation)
            )
            // 删除旧的节点，插入新的节点
            messageNodeDAO.deleteByConversation(conversation.id.toString())
            saveMessageNodes(conversation.id.toString(), conversation.messageNodes)
        }
        messageFtsManager.indexConversation(conversation)
    }

    suspend fun deleteConversation(conversation: Conversation) {
        invalidateDecodedCache(conversation.id)
        // 获取完整的 Conversation（包含 messageNodes）以正确清理文件
        val fullConversation = if (conversation.messageNodes.isEmpty()) {
            getConversationById(conversation.id) ?: conversation
        } else {
            conversation
        }
        messageFtsManager.deleteConversation(conversation.id.toString())
        database.withTransaction {
            // message_node 会通过 CASCADE 自动删除
            conversationDAO.delete(
                conversationToConversationEntity(conversation)
            )
        }
        filesManager.deleteChatFiles(fullConversation.files)
    }

    suspend fun searchMessages(
        keyword: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
        assistantId: Uuid? = null,
    ) = messageFtsManager.search(keyword, sort, assistantId?.let { listOf(it.toString()) })

    /** 只在 [assistantIds] 这些助手的对话里搜索 */
    suspend fun searchMessagesOfAssistants(keyword: String, assistantIds: Set<Uuid>) =
        messageFtsManager.search(keyword, MessageSearchSort.RELEVANCE, assistantIds.map { it.toString() })

    suspend fun rebuildAllIndexes(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }) {
        messageFtsManager.deleteAll()
        val allIds = conversationDAO.getAllIds()
        val total = allIds.size
        allIds.forEachIndexed { index, id ->
            val entity = conversationDAO.getConversationById(id) ?: return@forEachIndexed
            val nodes = loadMessageNodes(entity.id)
            val conversation = conversationEntityToConversation(entity, nodes)
            messageFtsManager.indexConversation(conversation)
            onProgress(index + 1, total)
        }
    }

    fun conversationToConversationEntity(conversation: Conversation): ConversationEntity {
        require(conversation.messageNodes.none { it.messages.any { message -> message.hasBase64Part() } })
        return ConversationEntity(
            id = conversation.id.toString(),
            title = conversation.title,
            nodes = "[]", // nodes 现在存储在单独的表中
            createAt = conversation.createAt.toEpochMilli(),
            updateAt = conversation.updateAt.toEpochMilli(),
            assistantId = conversation.assistantId.toString(),
            chatSuggestions = JsonInstant.encodeToString(conversation.chatSuggestions),
            isPinned = conversation.isPinned,
            customSystemPrompt = conversation.customSystemPrompt ?: "",
            modeInjectionIds = JsonInstant.encodeToString(conversation.modeInjectionIds),
            lorebookIds = JsonInstant.encodeToString(conversation.lorebookIds),
            workspaceCwd = conversation.workspaceCwd ?: "",
            folderId = conversation.folderId?.toString() ?: "",
            config = conversation.config?.let { JsonInstant.encodeToString(it) } ?: "",
        )
    }

    fun conversationEntityToConversation(
        conversationEntity: ConversationEntity,
        messageNodes: List<MessageNode>
    ): Conversation {
        return Conversation(
            id = Uuid.parse(conversationEntity.id),
            title = conversationEntity.title,
            messageNodes = messageNodes.filter { it.messages.isNotEmpty() },
            createAt = Instant.ofEpochMilli(conversationEntity.createAt),
            updateAt = Instant.ofEpochMilli(conversationEntity.updateAt),
            assistantId = Uuid.parse(conversationEntity.assistantId),
            chatSuggestions = JsonInstant.decodeFromString(conversationEntity.chatSuggestions),
            isPinned = conversationEntity.isPinned,
            customSystemPrompt = conversationEntity.customSystemPrompt.ifEmpty { null },
            modeInjectionIds = JsonInstant.decodeFromString(conversationEntity.modeInjectionIds),
            lorebookIds = JsonInstant.decodeFromString(conversationEntity.lorebookIds),
            workspaceCwd = conversationEntity.workspaceCwd.ifEmpty { null },
            folderId = conversationEntity.folderId.ifEmpty { null }?.let { Uuid.parse(it) },
            config = conversationEntity.config.ifEmpty { null }?.let { JsonInstant.decodeFromString(it) },
        )
    }

    fun getPinnedConversations(): Flow<List<Conversation>> {
        return conversationDAO
            .getPinnedConversations()
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity, emptyList())
                }
            }
    }

    suspend fun updatePinStatus(conversationId: Uuid, isPinned: Boolean) {
        invalidateDecodedCache(conversationId)
        conversationDAO.updatePinStatus(
            id = conversationId.toString(),
            isPinned = isPinned,
        )
    }

    suspend fun updateConversationAssistant(conversationId: Uuid, assistantId: Uuid) {
        invalidateDecodedCache(conversationId)
        conversationDAO.updateAssistantId(conversationId.toString(), assistantId.toString())
    }

    /**
     * 只更新会话持有的配置（含注入绑定和工作目录），不重写消息节点。
     */
    suspend fun updateConversationConfig(conversation: Conversation) {
        invalidateDecodedCache(conversation.id)
        conversationDAO.updateConfig(
            id = conversation.id.toString(),
            config = conversation.config?.let { JsonInstant.encodeToString(it) } ?: "",
            modeInjectionIds = JsonInstant.encodeToString(conversation.modeInjectionIds),
            lorebookIds = JsonInstant.encodeToString(conversation.lorebookIds),
            workspaceCwd = conversation.workspaceCwd ?: "",
        )
    }

    suspend fun updateTitle(conversationId: Uuid, title: String) {
        invalidateDecodedCache(conversationId)
        conversationDAO.updateTitle(conversationId.toString(), title)
    }

    suspend fun updateCustomSystemPrompt(conversationId: Uuid, customSystemPrompt: String?) {
        invalidateDecodedCache(conversationId)
        conversationDAO.updateCustomSystemPrompt(conversationId.toString(), customSystemPrompt ?: "")
    }

    suspend fun updateChatSuggestions(conversationId: Uuid, chatSuggestions: List<String>) {
        invalidateDecodedCache(conversationId)
        conversationDAO.updateChatSuggestions(
            id = conversationId.toString(),
            chatSuggestions = JsonInstant.encodeToString(chatSuggestions),
        )
    }

    /**
     * 单列更新会话的文件夹归属，folderId 为 null 表示移出文件夹（未归类）。
     */
    suspend fun updateConversationFolderId(conversationId: Uuid, folderId: Uuid?) {
        invalidateDecodedCache(conversationId)
        conversationDAO.updateFolderId(
            id = conversationId.toString(),
            folderId = folderId?.toString() ?: ""
        )
    }

    private fun conversationSummaryToConversation(entity: LightConversationEntity): Conversation {
        return Conversation(
            id = Uuid.parse(entity.id),
            assistantId = Uuid.parse(entity.assistantId),
            title = entity.title,
            isPinned = entity.isPinned,
            createAt = Instant.ofEpochMilli(entity.createAt),
            updateAt = Instant.ofEpochMilli(entity.updateAt),
            messageNodes = emptyList(),
            folderId = entity.folderId.ifEmpty { null }?.let { Uuid.parse(it) },
        )
    }

    private suspend fun loadMessageNodes(conversationId: String): List<MessageNode> {
        val favoriteNodeIds = favoriteDAO
            .getFavoriteNodeIdsOfConversation(conversationId)
            .mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }
            .toSet()

        return database.withTransaction {
            val nodes = mutableListOf<MessageNode>()
            var offset = 0
            val pageSize = 64
            while (true) {
                val page = try {
                    messageNodeDAO.getNodesOfConversationPaged(conversationId, pageSize, offset)
                } catch (e: SQLiteBlobTooBigException) {
                    e.printStackTrace()
                    offset += pageSize
                    continue
                } catch (e: CancellationException) {
                    // CancellationException 是 IllegalStateException 的子类，必须先重新抛出，否则取消后会在事务内无限循环
                    throw e
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                    offset += pageSize
                    continue
                }
                if (page.isEmpty()) break
                page.forEach { entity ->
                    val messages = JsonInstant.decodeFromString<List<UIMessage>>(entity.messages)
                    val nodeId = Uuid.parse(entity.id)
                    nodes.add(
                        MessageNode(
                            id = nodeId,
                            messages = messages,
                            selectIndex = entity.selectIndex,
                            isFavorite = favoriteNodeIds.contains(nodeId)
                        )
                    )
                }
                offset += page.size
            }
            nodes
        }
    }

    private suspend fun saveMessageNodes(conversationId: String, nodes: List<MessageNode>) {
        // 消息体变了，缓存必然失效（insert/updateConversation 已先失效一次，这里兜底）
        runCatching { invalidateDecodedCache(Uuid.parse(conversationId)) }
        val entities = nodes.mapIndexed { index, node ->
            MessageNodeEntity(
                id = node.id.toString(),
                conversationId = conversationId,
                nodeIndex = index,
                messages = JsonInstant.encodeToString(node.messages),
                selectIndex = node.selectIndex
            )
        }
        messageNodeDAO.insertAll(entities)
    }
}

/**
 * 轻量级的会话查询结果，不包含 nodes 和 suggestions 字段
 */
data class LightConversationEntity(
    val id: String,
    val assistantId: String,
    val title: String,
    val isPinned: Boolean,
    val createAt: Long,
    val updateAt: Long,
    val folderId: String = "",
)

data class ConversationPageResult(
    val items: List<Conversation>,
    val nextOffset: Int?,
)
