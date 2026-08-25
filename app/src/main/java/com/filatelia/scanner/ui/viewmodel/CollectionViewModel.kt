package com.filatelia.scanner.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filatelia.scanner.data.SortOrder
import com.filatelia.scanner.data.StampEntity
import com.filatelia.scanner.data.StampRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CollectionViewModel(private val repository: StampRepository) : ViewModel() {

    private val _sortOrder = MutableStateFlow(SortOrder.RECIENTES)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val stamps: StateFlow<List<StampEntity>> = combine(_sortOrder, _searchQuery) { order, query -> order to query }
        .flatMapLatest { (order, query) ->
            if (query.isBlank()) repository.observeStamps(order) else repository.search(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteStamp(stamp: StampEntity) {
        viewModelScope.launch { repository.deleteStamp(stamp) }
    }
}
