package jp.yamato373.fix.price;

import static quickfix.Acceptor.*;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import quickfix.Application;
import quickfix.ConfigError;
import quickfix.DefaultMessageFactory;
import quickfix.FieldConvertError;
import quickfix.FileStoreFactory;
import quickfix.LogFactory;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.ScreenLogFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.SocketAcceptor;
import quickfix.mina.acceptor.DynamicAcceptorSessionProvider;
import quickfix.mina.acceptor.DynamicAcceptorSessionProvider.TemplateMapping;

@Component
@Slf4j
public class PriceAcceptor {

	private SocketAcceptor acceptor;
    private Map<InetSocketAddress, List<TemplateMapping>> dynamicSessionMappings = new HashMap<>();

    SessionSettings sessionSettings;

    private PriceApplication priceApplication;

    @Autowired
    public PriceAcceptor(PriceApplication priceApplication) throws ConfigError, FieldConvertError{
    	sessionSettings = new SessionSettings("price.cfg");
    	priceApplication.setSessionSettings(sessionSettings);
    	this.priceApplication = priceApplication;
    }

    @PostConstruct
	public void init() {
		try {
			MessageStoreFactory messageStoreFactory = new FileStoreFactory(sessionSettings);
			LogFactory logFactory = new ScreenLogFactory(true, true, true);
			MessageFactory messageFactory = new DefaultMessageFactory();

			acceptor = new SocketAcceptor(priceApplication, messageStoreFactory, sessionSettings, logFactory, messageFactory);

			configureDynamicSessions(sessionSettings, priceApplication, messageStoreFactory, logFactory, messageFactory);

			acceptor.start();

		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

    @PreDestroy
	public void stop() {
    	acceptor.stop();
	}

    private void configureDynamicSessions(SessionSettings settings, Application application,
			MessageStoreFactory messageStoreFactory, LogFactory logFactory, MessageFactory messageFactory)
			throws ConfigError, FieldConvertError {

		Iterator<SessionID> sectionIterator = settings.sectionIterator();
		while (sectionIterator.hasNext()) {
			SessionID sessionID = sectionIterator.next();
			if (isSessionTemplate(settings, sessionID)) {
				InetSocketAddress address = getAcceptorSocketAddress(settings, sessionID);
				getMappings(address).add(new TemplateMapping(sessionID, sessionID));
			}
		}

		for (Map.Entry<InetSocketAddress, List<TemplateMapping>> entry : dynamicSessionMappings.entrySet()) {
			acceptor.setSessionProvider(entry.getKey(), new DynamicAcceptorSessionProvider(settings, entry.getValue(),
					application, messageStoreFactory, logFactory, messageFactory));
		}
	}

	private List<TemplateMapping> getMappings(InetSocketAddress address) {
		List<TemplateMapping> mappings = dynamicSessionMappings.get(address);
		if (mappings == null) {
			mappings = new ArrayList<>();
			dynamicSessionMappings.put(address, mappings);
		}
		return mappings;
	}

	private InetSocketAddress getAcceptorSocketAddress(SessionSettings settings, SessionID sessionID)
			throws ConfigError, FieldConvertError {
		String acceptorHost = "0.0.0.0";
		if (settings.isSetting(sessionID, SETTING_SOCKET_ACCEPT_ADDRESS)) {
			acceptorHost = settings.getString(sessionID, SETTING_SOCKET_ACCEPT_ADDRESS);
		}
		int acceptorPort = (int) settings.getLong(sessionID, SETTING_SOCKET_ACCEPT_PORT);

		return new InetSocketAddress(acceptorHost, acceptorPort);
	}

	private boolean isSessionTemplate(SessionSettings settings, SessionID sessionID)
			throws ConfigError, FieldConvertError {
		return settings.isSetting(sessionID, SETTING_ACCEPTOR_TEMPLATE)
				&& settings.getBool(sessionID, SETTING_ACCEPTOR_TEMPLATE);
	}
}
