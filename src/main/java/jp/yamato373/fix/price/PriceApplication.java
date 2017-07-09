package jp.yamato373.fix.price;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jp.yamato373.fix.MarketDataProvider;
import lombok.extern.slf4j.Slf4j;
import quickfix.Application;
import quickfix.ConfigError;
import quickfix.DataDictionaryProvider;
import quickfix.DoNotSend;
import quickfix.FieldConvertError;
import quickfix.FieldNotFound;
import quickfix.FixVersions;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.LogUtil;
import quickfix.Message;
import quickfix.MessageCracker;
import quickfix.MessageUtils;
import quickfix.RejectLogon;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.SessionSettings;
import quickfix.UnsupportedMessageType;
import quickfix.field.ApplVerID;
import quickfix.field.MDEntryDate;
import quickfix.field.MDEntryPx;
import quickfix.field.MDEntrySize;
import quickfix.field.MDEntryTime;
import quickfix.field.MDEntryType;
import quickfix.field.MDReqID;
import quickfix.field.MDUpdateAction;
import quickfix.field.MsgType;
import quickfix.field.OrdType;
import quickfix.field.SubscriptionRequestType;
import quickfix.field.Symbol;
import quickfix.field.Text;
import quickfix.fix44.MarketDataIncrementalRefresh;
import quickfix.fix44.MarketDataSnapshotFullRefresh;

@Component
@Slf4j
public class PriceApplication extends MessageCracker implements Application {

	private static final String DEFAULT_MARKET_PRICE_KEY = "DefaultMarketPrice";
	private static final String VALID_ORDER_TYPES_KEY = "ValidOrderTypes";

	private final HashSet<String> validOrderTypes = new HashSet<>();
	private MarketDataProvider marketDataProvider;

	private static final int INTERVAL = 5000;
	private static final String SYMBOL = "USD/JPY";
	private final String INDICATIVE_TEXT = "Indicative";

	@Autowired
	RateGeneratWorker rateGeneratWorker;

	private String mDReqID;

	ExecutorService exec = Executors.newSingleThreadExecutor();
	Future<?> future = null;

	public void setSessionSettings(SessionSettings sessionSettings) throws ConfigError, FieldConvertError {
		initializeValidOrderTypes(sessionSettings);
		initializeMarketDataProvider(sessionSettings);
	}

	private void initializeMarketDataProvider(SessionSettings settings) throws ConfigError, FieldConvertError {
		if (settings.isSetting(DEFAULT_MARKET_PRICE_KEY)) {
			if (marketDataProvider == null) {
				final double defaultMarketPrice = settings.getDouble(DEFAULT_MARKET_PRICE_KEY);
				marketDataProvider = new MarketDataProvider() {
					public double getAsk(String symbol) {
						return defaultMarketPrice;
					}

					public double getBid(String symbol) {
						return defaultMarketPrice;
					}
				};
			} else {
				log.warn("Ignoring " + DEFAULT_MARKET_PRICE_KEY + " since provider is already defined.");
			}
		}
	}

	private void initializeValidOrderTypes(SessionSettings settings) throws ConfigError, FieldConvertError {
		if (settings.isSetting(VALID_ORDER_TYPES_KEY)) {
			List<String> orderTypes = Arrays
					.asList(settings.getString(VALID_ORDER_TYPES_KEY).trim().split("\\s*,\\s*"));
			validOrderTypes.addAll(orderTypes);
		} else {
			validOrderTypes.add(OrdType.LIMIT + "");
		}
	}

	@Override
	public void onCreate(SessionID sessionID) {
		Session.lookupSession(sessionID).getLog().onEvent("Valid order types: " + validOrderTypes);
	}

	@Override
	public void onLogon(SessionID sessionID) {
	}

	@Override
	public void onLogout(SessionID sessionID) {
	}

	@Override
	public void toAdmin(quickfix.Message message, SessionID sessionID) {
	}

	@Override
	public void toApp(quickfix.Message message, SessionID sessionID) throws DoNotSend {
	}

	@Override
	public void fromAdmin(quickfix.Message message, SessionID sessionID)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
	}

	@Override
	public void fromApp(quickfix.Message message, SessionID sessionID)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {

		if (MsgType.MARKET_DATA_REQUEST.equals(message.getHeader().getString(MsgType.FIELD))) {

			// 初回リクエスト、またはアンサブスクライブ後のリクエスト
			if (SubscriptionRequestType.SNAPSHOT_PLUS_UPDATES == message.getChar(SubscriptionRequestType.FIELD)
					&& StringUtils.isEmpty(mDReqID)) {
				crack(message, sessionID);
				startSendIncrementalRefresh(sessionID);

				// アンサブスクライブ
			} else if (SubscriptionRequestType.DISABLE_PREVIOUS_SNAPSHOT_PLUS_UPDATE_REQUEST
					== message.getChar(SubscriptionRequestType.FIELD)) {
				log.info("アンサブスクライブきたよ！");
				future.cancel(true);
				mDReqID = null;
			}
		}
	}

	public void onMessage(quickfix.fix44.MarketDataRequest request, SessionID sessionID)
			throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
		try {

			MarketDataSnapshotFullRefresh marketDataSnapshotFullRefresh = new MarketDataSnapshotFullRefresh();

			marketDataSnapshotFullRefresh.set(request.getMDReqID());
			marketDataSnapshotFullRefresh.set(new Symbol(SYMBOL));

			MarketDataSnapshotFullRefresh.NoMDEntries noMDEntries = new MarketDataSnapshotFullRefresh.NoMDEntries();

			// 1/10の確率で気配値で送信
			boolean indicativeChangeFlg = (int)(Math.random()*10) == 0;

			noMDEntries.set(new MDEntryType(MDEntryType.BID));
			noMDEntries.set(new MDEntryPx(rateGeneratWorker.getRate().getBidPx().doubleValue()));
			noMDEntries.set(new MDEntrySize(1000000));
			noMDEntries.set(new MDEntryDate(new Date()));
			noMDEntries.set(new MDEntryTime(new Date()));
			if (indicativeChangeFlg){
				noMDEntries.set(new MDEntrySize(0));
				noMDEntries.set(new Text(INDICATIVE_TEXT));
			}
			marketDataSnapshotFullRefresh.addGroup(noMDEntries);

			noMDEntries.set(new MDEntryType(MDEntryType.OFFER));
			noMDEntries.set(new MDEntryPx(rateGeneratWorker.getRate().getAskPx().doubleValue()));
			noMDEntries.set(new MDEntrySize(1000000));
			noMDEntries.set(new MDEntryDate(new Date()));
			noMDEntries.set(new MDEntryTime(new Date()));
			if (indicativeChangeFlg){
				noMDEntries.set(new MDEntrySize(0));
				noMDEntries.set(new Text(INDICATIVE_TEXT));
			}
			marketDataSnapshotFullRefresh.addGroup(noMDEntries);

			sendMessage(sessionID, marketDataSnapshotFullRefresh);

			mDReqID = request.getMDReqID().getValue();
		} catch (RuntimeException e) {
			LogUtil.logThrowable(sessionID, e.getMessage(), e);
		}
	}

	public void startSendIncrementalRefresh(SessionID sessionID) {
		future = exec.submit(() -> {
			log.info("IncrementalRefresh送信タスク開始したよ！");
			boolean indicativeFlg = false;
			while (true) {

				try {
					TimeUnit.MILLISECONDS.sleep(INTERVAL);
				} catch (InterruptedException e) {
					// タスクのクリア時に割込みされるが問題なし
				}

				MarketDataIncrementalRefresh marketDataIncrementalRefresh = new MarketDataIncrementalRefresh();

				marketDataIncrementalRefresh.set(new MDReqID(mDReqID));

				MarketDataIncrementalRefresh.NoMDEntries noMDEntries = new MarketDataIncrementalRefresh.NoMDEntries();

				// 1/2の確率でプライスを更新
				boolean pxChangeFlg = (int)(Math.random()*10) < 5;
				// 1/10の確率でアマウント更新
				boolean amtChangeFlg = (int)(Math.random()*10) == 0 || indicativeFlg;
				int amt = (int)(Math.random()*10+1)*100000;
				// 1/10の確率で気配値で送信
				boolean indicativeChangeFlg = (int)(Math.random()*10) == 0;

				noMDEntries.set(new MDUpdateAction(MDUpdateAction.CHANGE));
				noMDEntries.set(new MDEntryType(MDEntryType.BID));
				noMDEntries.set(new Symbol(SYMBOL));
				if (pxChangeFlg){
					noMDEntries.set(new MDEntryPx(rateGeneratWorker.getRate().getBidPx().doubleValue()));
				}
				if (amtChangeFlg){
					noMDEntries.set(new MDEntrySize(amt));
				}
				noMDEntries.set(new MDEntryDate(new Date()));
				noMDEntries.set(new MDEntryTime(new Date()));
				if (indicativeChangeFlg){
					noMDEntries.set(new MDEntrySize(0));
					noMDEntries.set(new Text(INDICATIVE_TEXT));
				}
				marketDataIncrementalRefresh.addGroup(noMDEntries);

				noMDEntries.set(new MDUpdateAction(MDUpdateAction.CHANGE));
				noMDEntries.set(new MDEntryType(MDEntryType.OFFER));
				noMDEntries.set(new Symbol(SYMBOL));
				if (pxChangeFlg){
					noMDEntries.set(new MDEntryPx(rateGeneratWorker.getRate().getAskPx().doubleValue()));
				}
				if (amtChangeFlg){
					noMDEntries.set(new MDEntrySize(amt));
				}
				noMDEntries.set(new MDEntryDate(new Date()));
				noMDEntries.set(new MDEntryTime(new Date()));
				if (indicativeChangeFlg){
					noMDEntries.set(new MDEntrySize(0));
					noMDEntries.set(new Text(INDICATIVE_TEXT));
				}
				marketDataIncrementalRefresh.addGroup(noMDEntries);

				indicativeFlg = indicativeChangeFlg;

				sendMessage(sessionID, marketDataIncrementalRefresh);
			}
		});
	}

	private void sendMessage(SessionID sessionID, Message message) {
		try {
			Session session = Session.lookupSession(sessionID);
			if (session == null) {
				throw new SessionNotFound(sessionID.toString());
			}

			DataDictionaryProvider dataDictionaryProvider = session.getDataDictionaryProvider();
			if (dataDictionaryProvider != null) {
				try {
					dataDictionaryProvider.getApplicationDataDictionary(getApplVerID(session, message))
							.validate(message, true);
				} catch (Exception e) {
					LogUtil.logThrowable(sessionID, "Outgoing message failed validation: " + e.getMessage(), e);
					return;
				}
			}

			session.send(message);
		} catch (SessionNotFound e) {
			log.error(e.getMessage(), e);
		}
	}

	private ApplVerID getApplVerID(Session session, Message message) {
		String beginString = session.getSessionID().getBeginString();
		if (FixVersions.BEGINSTRING_FIXT11.equals(beginString)) {
			return new ApplVerID(ApplVerID.FIX50);
		} else {
			return MessageUtils.toApplVerID(beginString);
		}
	}
}
