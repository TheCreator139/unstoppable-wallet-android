package io.horizontalsystems.bankwallet.modules.market.metricspage

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.bankwallet.core.providers.Translator
import io.horizontalsystems.bankwallet.core.stats.StatEvent
import io.horizontalsystems.bankwallet.core.stats.StatPage
import io.horizontalsystems.bankwallet.core.stats.stat
import io.horizontalsystems.bankwallet.core.stats.statField
import io.horizontalsystems.bankwallet.core.stats.statPage
import io.horizontalsystems.bankwallet.entities.ViewState
import io.horizontalsystems.bankwallet.modules.market.MarketField
import io.horizontalsystems.bankwallet.modules.market.MarketItem
import io.horizontalsystems.bankwallet.modules.market.MarketModule
import io.horizontalsystems.bankwallet.modules.market.MarketViewItem
import io.horizontalsystems.bankwallet.modules.metricchart.MetricsType
import io.horizontalsystems.bankwallet.ui.compose.Select
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow

class MetricsPageViewModel(
    private val service: MetricsPageService,
) : ViewModel() {

    private val marketFields = MarketField.values().toList()
    private var marketField: MarketField
    private var marketItems: List<MarketItem> = listOf()
    private val metricsType: MetricsType = service.metricsType
    private val statPage: StatPage =  metricsType.statPage

    val isRefreshingLiveData = MutableLiveData<Boolean>()
    val marketLiveData = MutableLiveData<MetricsPageModule.MarketData>()
    val viewStateLiveData = MutableLiveData<ViewState>(ViewState.Loading)
    var header = MarketModule.Header(
        title = Translator.getString(metricsType.title),
        description = Translator.getString(metricsType.description),
        icon = metricsType.headerIcon
    )

    init {
        marketField = when (metricsType) {
            MetricsType.Volume24h -> MarketField.Volume
            MetricsType.TotalMarketCap,
            MetricsType.DefiCap,
            MetricsType.BtcDominance,
            MetricsType.TvlInDefi -> MarketField.MarketCap
        }

        viewModelScope.launch {
            service.marketItemsObservable.asFlow().collect { marketItemsDataState ->
                marketItemsDataState.viewState?.let {
                    viewStateLiveData.postValue(it)
                }

                marketItemsDataState?.dataOrNull?.let {
                    marketItems = it
                    syncMarketItems(it)
                }
            }
        }

        service.start()
    }

    private fun syncMarketItems(marketItems: List<MarketItem>) {
        marketLiveData.postValue(marketData(marketItems))
    }

    private fun marketData(marketItems: List<MarketItem>): MetricsPageModule.MarketData {
        val menu = MetricsPageModule.Menu(service.sortDescending, Select(marketField, marketFields))
        val marketViewItems = marketItems.map { MarketViewItem.create(it, marketField) }
        return MetricsPageModule.MarketData(menu, marketViewItems)
    }

    private fun refreshWithMinLoadingSpinnerPeriod() {
        service.refresh()
        viewModelScope.launch {
            isRefreshingLiveData.postValue(true)
            delay(1000)
            isRefreshingLiveData.postValue(false)
        }
    }

    fun onToggleSortType() {
        service.sortDescending = !service.sortDescending

        stat(page = statPage, event = StatEvent.ToggleSortDirection)
    }

    fun onSelectMarketField(marketField: MarketField) {
        this.marketField = marketField
        syncMarketItems(marketItems)

        stat(page = statPage, event = StatEvent.SwitchField(marketField.statField))
    }

    fun refresh() {
        refreshWithMinLoadingSpinnerPeriod()

        stat(page = statPage, event = StatEvent.Refresh)
    }

    fun onErrorClick() {
        refreshWithMinLoadingSpinnerPeriod()
    }

    override fun onCleared() {
        service.stop()
    }
}
