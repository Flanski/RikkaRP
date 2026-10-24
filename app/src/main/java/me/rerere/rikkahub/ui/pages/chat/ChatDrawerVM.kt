package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.MemoryConsolidationScheduler
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.memoryId
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.ui.UpdateHost
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

class ChatDrawerVM(
    private val context: Application,
    private val settingsStore: SettingsStore,
    conversationRepo: ConversationRepository,
    private val folderRepo: FolderRepository,
    private val chatService: ChatService,
    private val memoryConsolidationScheduler: MemoryConsolidationScheduler,
    private val filesManager: FilesManager,
    override val updateChecker: UpdateChecker,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel(), UpdateHost {

    private val assistantIdFlow = settingsStore.settingsFlow
        .map { it.assistantId }
        .distinctUntilChanged()

    private val sortOrderFlow = settingsStore.settingsFlow
        .map { it.displaySetting.conversationSortOrder }
        .distinctUntilChanged()

    // 当前选中的文件夹筛选，null 表示「未归类」视图
    private val _selectedFolderId = MutableStateFlow<Uuid?>(null)
    val selectedFolderId: StateFlow<Uuid?> = _selectedFolderId.asStateFlow()

    // 当前助手的文件夹列表（Room Flow，增删改自动刷新）
    val folders: StateFlow<List<Folder>> = assistantIdFlow
        .flatMapLatest { folderRepo.getFoldersOfAssistant(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前助手的记忆库是否正在后台整理；没开记忆的助手即使共用的全局记忆库在整理也不显示
    val memoryDreaming: StateFlow<Boolean> = settingsStore.settingsFlow
        .map { settings -> settings.getCurrentAssistant().takeIf { it.enableMemory }?.memoryId }
        .distinctUntilChanged()
        .flatMapLatest { memoryId ->
            if (memoryId == null) flowOf(false) else memoryConsolidationScheduler.statusOf(memoryId).map { it.running }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val conversations: Flow<PagingData<ConversationListItem>> =
        combine(assistantIdFlow, _selectedFolderId, sortOrderFlow) { assistantId, folderId, sortOrder ->
            Triple(assistantId, folderId, sortOrder)
        }
            .flatMapLatest { (assistantId, folderId, sortOrder) ->
                val paging = if (folderId == null) {
                    conversationRepo.getUnfiledConversationsOfAssistantPaging(assistantId, sortOrder)
                } else {
                    conversationRepo.getConversationsOfFolderPaging(folderId, sortOrder)
                }
                paging.map { pagingData ->
                    pagingData
                        .map { ConversationListItem.Item(it) }
                        .insertSeparators<ConversationListItem.Item, ConversationListItem> { before, after ->
                            when {
                                after == null -> null
                                after.conversation.isPinned -> {
                                    if (before == null) ConversationListItem.PinnedHeader else null
                                }

                                else -> {
                                    // 日期分组跟随排序依据：按创建时间排序时以创建日期分组
                                    val afterDate = after.conversation.sortDate(sortOrder)
                                    val sameGroup = before != null &&
                                        !before.conversation.isPinned &&
                                        before.conversation.sortDate(sortOrder) == afterDate
                                    if (sameGroup) {
                                        null
                                    } else {
                                        ConversationListItem.DateHeader(
                                            date = afterDate,
                                            label = getDateLabel(afterDate),
                                            sortOrder = sortOrder,
                                        )
                                    }
                                }
                            }
                        }
                }
            }
            .cachedIn(viewModelScope)

    val scrollIndex: Int get() = savedStateHandle["scrollIndex"] ?: 0
    val scrollOffset: Int get() = savedStateHandle["scrollOffset"] ?: 0

    init {
        // 助手切换时重置文件夹筛选，回到「聊天」视图，
        // 避免继续显示上一个助手文件夹内的会话（文件夹是助手内分组）
        viewModelScope.launch {
            assistantIdFlow.collect {
                _selectedFolderId.value = null
            }
        }
    }

    fun saveScrollPosition(index: Int, offset: Int) {
        savedStateHandle["scrollIndex"] = index
        savedStateHandle["scrollOffset"] = offset
    }

    fun selectFolder(folderId: Uuid?) {
        _selectedFolderId.value = folderId
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val assistantId = assistantIdFlow.first()
            folderRepo.createFolder(assistantId, trimmed)
        }
    }

    fun renameFolder(folderId: Uuid, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            folderRepo.renameFolder(folderId, trimmed)
        }
    }

    /**
     * 删除文件夹。若文件夹内有正在生成回复的会话，拒绝删除并返回 false（UI 层据此提示用户）。
     */
    fun deleteFolder(folderId: Uuid): Boolean {
        if (chatService.hasGeneratingConversationInFolder(folderId)) {
            return false
        }
        viewModelScope.launch {
            // 经 ChatService 删除：会同步清空活跃 session 内存态的 folderId，避免整对象保存写回已删文件夹
            chatService.deleteFolder(folderId)
            if (_selectedFolderId.value == folderId) {
                _selectedFolderId.value = null
            }
        }
        return true
    }

    fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        viewModelScope.launch {
            // 经 ChatService 移动：活跃会话会先同步内存态，避免后续整对象保存覆盖 folder_id
            chatService.moveConversationToFolder(conversationId, folderId)
        }
    }

    private fun Conversation.sortDate(sortOrder: ConversationSortOrder): LocalDate {
        val instant = when (sortOrder) {
            ConversationSortOrder.UPDATE_TIME -> updateAt
            ConversationSortOrder.CREATE_TIME -> createAt
        }
        return instant.atZone(ZoneId.systemDefault()).toLocalDate()
    }

    // 更新检查：判定条件与 ChatVM 保持一致
    override val updateState = settingsStore.settingsFlow
        .map { settings ->
            !BuildConfig.IS_PLAY_STORE &&
                !settings.init &&
                settings.displaySetting.updateCheckDisabledUntilEpochMillis <= System.currentTimeMillis()
        }
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (enabled) updateChecker.updateState else flowOf(UiState.Loading)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            UiState.Loading,
        )

    // ===== RikkaRP: 消息页独立后新增（原属 ChatVM，按对话 id 操作，不依赖具体实例）=====

    /** 当前正在生成的对话 id 集合，列表里显示「生成中」标记 */
    val conversationJobs = chatService
        .getConversationJobs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 更新设置。切换用户头像时删除旧的图片文件，避免垃圾堆积。 */
    fun updateSettings(fn: (Settings) -> Settings): Job {
        return viewModelScope.launch {
            var oldSettings: Settings? = null
            var newSettings: Settings? = null
            settingsStore.update { old ->
                fn(old).also { new ->
                    oldSettings = old
                    newSettings = new
                }
            }
            val oldAvatar = (oldSettings ?: return@launch).displaySetting.userAvatar
            val newAvatar = (newSettings ?: return@launch).displaySetting.userAvatar
            if (oldAvatar is Avatar.Image && oldAvatar != newAvatar) {
                filesManager.deleteChatFiles(listOf(oldAvatar.url.toUri()))
            }
        }
    }

    // 注：conversations 是 Paging3 的 Flow（由 Room 驱动），删除后列表会自动更新，
    // 这里不需要（也没法）调用 refresh()——那是 LazyPagingItems 的方法。
    fun deleteConversation(conversation: Conversation): Job =
        viewModelScope.launch {
            chatService.deleteConversation(conversation.id)
        }

    fun updatePinnedStatus(conversation: Conversation) {
        viewModelScope.launch {
            chatService.toggleConversationPinned(conversation.id)
        }
    }

    fun moveConversationToAssistant(conversation: Conversation, targetAssistantId: Uuid) {
        viewModelScope.launch {
            chatService.moveConversationToAssistant(conversation.id, targetAssistantId)
        }
    }

    fun generateTitle(conversation: Conversation, force: Boolean = false) {
        viewModelScope.launch {
            chatService.generateTitle(conversation.id, force)
        }
    }

    private fun getDateLabel(date: LocalDate): String {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        return when (date) {
            today -> context.getString(R.string.chat_page_today)
            yesterday -> context.getString(R.string.chat_page_yesterday)
            else -> date.toLocalString(date.year != today.year)
        }
    }
}
