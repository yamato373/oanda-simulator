package jp.yamato373.fix;

public interface MarketDataProvider {

    double getBid(String symbol);
    double getAsk(String symbol);
}
