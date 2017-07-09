package jp.yamato373.fix.order;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.stereotype.Component;

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
import quickfix.field.AvgPx;
import quickfix.field.CumQty;
import quickfix.field.ExecID;
import quickfix.field.ExecType;
import quickfix.field.LastPx;
import quickfix.field.LastQty;
import quickfix.field.LeavesQty;
import quickfix.field.OrdRejReason;
import quickfix.field.OrdStatus;
import quickfix.field.OrdType;
import quickfix.field.OrderID;
import quickfix.field.TransactTime;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;

@Component
@Slf4j
public class OrderApplication extends MessageCracker implements Application {

	private int clOrdIdCounterr = 0;
	private int execIdCounterr = 0;

	private static final String DEFAULT_MARKET_PRICE_KEY = "DefaultMarketPrice";
	private static final String VALID_ORDER_TYPES_KEY = "ValidOrderTypes";

	private final HashSet<String> validOrderTypes = new HashSet<>();
	private MarketDataProvider marketDataProvider;

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

		crack(message, sessionID);
	}

	public void onMessage(NewOrderSingle newOrderSingle, SessionID sessionID)
			throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {

		log.info("オーダー来たよ！" + newOrderSingle);

		// 1/10の確率でリジェクト
		boolean rejectFlg = (int) (Math.random() * 10) == 0;

		ExecutionReport executionReport;

		if (rejectFlg) {
			// リジェクト
			executionReport = new ExecutionReport(new OrderID(generatOrderId()), new ExecID(generatExecId()),
					new ExecType(ExecType.REJECTED), new OrdStatus(OrdStatus.REJECTED), newOrderSingle.getSide(),
					new LeavesQty(0), new CumQty(0), new AvgPx(0));
			executionReport.set(new OrdRejReason(OrdRejReason.OTHER));
			executionReport.set(new LastQty(0));
			executionReport.set(new LastPx(0));
		} else {
			// 約定
			executionReport = new ExecutionReport(new OrderID(generatOrderId()), new ExecID(generatExecId()),
					new ExecType(ExecType.TRADE), new OrdStatus(OrdStatus.FILLED), newOrderSingle.getSide(),
					new LeavesQty(0), new CumQty(newOrderSingle.getOrderQty().getValue()),
					new AvgPx(newOrderSingle.getPrice().getValue()));
			executionReport.set(new LastQty(newOrderSingle.getOrderQty().getValue()));
			executionReport.set(new LastPx(newOrderSingle.getPrice().getValue()));
		}
		executionReport.set(newOrderSingle.getClOrdID());
		executionReport.set(newOrderSingle.getAccount());
		executionReport.set(newOrderSingle.getHandlInst());
		executionReport.set(newOrderSingle.getSymbol());
		executionReport.set(newOrderSingle.getOrderQty());
		executionReport.set(newOrderSingle.getOrdType());
		executionReport.set(newOrderSingle.getPrice());
		executionReport.set(newOrderSingle.getTimeInForce());
		executionReport.set(new TransactTime(new Date()));

		sendMessage(sessionID, executionReport);
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

	private String generatOrderId() {
		return Long.toString(System.currentTimeMillis() + (clOrdIdCounterr++));
	}

	private String generatExecId() {
		return Long.toString(System.currentTimeMillis() + (execIdCounterr++));
	}
}
