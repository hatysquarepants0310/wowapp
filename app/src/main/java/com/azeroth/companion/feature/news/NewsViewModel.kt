package com.azeroth.companion.feature.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.data.NewsArticle
import com.azeroth.companion.data.NewsItem
import com.azeroth.companion.data.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewsUiState(
    val items: List<NewsItem> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val page: Int = 1,
    val error: String? = null,
)

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val repository: NewsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NewsUiState())
    val state: StateFlow<NewsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null, page = 1) }
        viewModelScope.launch {
            repository.headlines(1)
                .onSuccess { items -> _state.update { it.copy(items = items, loading = false) } }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.message) }
                }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.loading || current.items.isEmpty()) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val next = current.page + 1
            repository.headlines(next)
                .onSuccess { more ->
                    _state.update { state ->
                        val known = state.items.map { it.id }.toSet()
                        state.copy(
                            items = state.items + more.filterNot { it.id in known },
                            page = next,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }
}

data class ArticleUiState(
    val article: NewsArticle? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ArticleViewModel @Inject constructor(
    private val repository: NewsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ArticleUiState())
    val state: StateFlow<ArticleUiState> = _state.asStateFlow()

    private var loaded: String? = null

    fun load(articleId: String) {
        if (loaded == articleId) return
        loaded = articleId
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.article("/news/$articleId")
                .onSuccess { article ->
                    _state.update { it.copy(article = article, loading = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.message) }
                }
        }
    }
}
