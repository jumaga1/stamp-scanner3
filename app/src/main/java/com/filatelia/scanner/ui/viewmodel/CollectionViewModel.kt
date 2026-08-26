package com.filatelia.scanner.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filatelia.scanner.data.SortOrder
import com.filatelia.scanner.data.StampEntity
import com.filatelia.scanner.data.StampRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CollectionViewModel(
    private val stampRepository: StampRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.RECENT)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val stamps: StateFlow<List<StampEntity>> = combine(_searchQuery, _sortOrder) { query, sort ->
        Pair(query, sort)
    }.flatMapLatest { (query, sort) ->
        stampRepository.observeStamps(query, sort)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onSortOrderChanged(newOrder: SortOrder) {
        _sortOrder.value = newOrder
    }

    fun deleteStamp(stamp: StampEntity) {
        viewModelScope.launch {
            stampRepository.delete(stamp)
        }
    }
}
