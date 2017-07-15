# Counterpartyシュミュレータ

## Web API

### 設定変更

* プライス配信間隔
    - http://localhost:8080/price/settings/?sendInterval=5000

* プライス移動上限
    - http://localhost:8080/price/settings/?upperLimit=125

* プライス移動下限
    - http://localhost:8080/price/settings/?lowerLimit=70

* スプレッド
    - http://localhost:8080/price/settings/?spread=0.004

* プライス移動間隔
    - http://localhost:8080/price/settings/?move=0.001

* プライス生成間隔
    - http://localhost:8080/price/settings/?rateGenerateInterval=100
