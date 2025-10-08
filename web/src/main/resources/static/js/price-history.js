(function () {
    function parsePriceHistory(raw) {
        if (!raw) {
            return [];
        }

        let data = [];
        try {
            data = JSON.parse(raw);
        } catch (error) {
            console.error('Failed to parse price history data', error);
            return [];
        }

        if (!Array.isArray(data)) {
            return [];
        }

        return data
            .filter(function (point) {
                return point && point.date && point.price !== undefined && point.price !== null;
            })
            .map(function (point) {
                var normalizedPrice = point.price;
                if (typeof normalizedPrice === 'string') {
                    normalizedPrice = normalizedPrice.replace(',', '.');
                }
                var numericPrice = Number.parseFloat(normalizedPrice);
                if (!Number.isFinite(numericPrice)) {
                    return null;
                }
                return {
                    date: point.date,
                    price: numericPrice
                };
            })
            .filter(function (point) { return point !== null; })
            .sort(function (a, b) {
                return new Date(a.date).getTime() - new Date(b.date).getTime();
            });
    }

    function initChart() {
        var chartElement = document.getElementById('priceHistoryChart');
        if (!chartElement) {
            return;
        }

        if (!window.Chart) {
            console.error('Chart.js was not loaded');
            return;
        }

        if (Chart.registerables) {
            Chart.register.apply(Chart, Chart.registerables);
        }

        var priceHistory = parsePriceHistory(chartElement.dataset.priceHistory);
        if (priceHistory.length === 0) {
            return;
        }

        var context = chartElement.getContext('2d');
        if (!context) {
            console.warn('Unable to initialise the price history chart because a 2D context could not be acquired.');
            return;
        }

        new Chart(context, {
            type: 'line',
            data: {
                labels: priceHistory.map(function (point) { return point.date; }),
                datasets: [{
                    label: 'Price per item',
                    data: priceHistory.map(function (point) { return point.price; }),
                    fill: false,
                    borderColor: 'rgb(13, 110, 253)',
                    backgroundColor: 'rgba(13, 110, 253, 0.12)',
                    tension: 0.2,
                    pointRadius: 4,
                    pointHoverRadius: 6
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false
                    },
                    tooltip: {
                        callbacks: {
                            label: function (context) {
                                var value = context.parsed.y;
                                return value !== undefined ? value.toFixed(2) + ' SEK per item' : '';
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        ticks: {
                            autoSkip: true,
                            maxTicksLimit: 8
                        }
                    },
                    y: {
                        beginAtZero: false,
                        ticks: {
                            callback: function (value) {
                                return Number.parseFloat(value).toFixed(2);
                            }
                        }
                    }
                },
                interaction: {
                    intersect: false,
                    mode: 'nearest'
                }
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initChart);
    } else {
        initChart();
    }
})();
