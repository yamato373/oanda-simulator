package jp.yamato373.fix.util;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@ConfigurationProperties(prefix = "settings")
@Data
public class Settings {
	private String cp;
	private String symbol;
	private int sendInterval;
	private String indicativeText;
	private BigDecimal upperLimit;
	private BigDecimal lowerLimit;
	private BigDecimal spread;
	private BigDecimal move;
	private long rateGenerateInterval;
}
