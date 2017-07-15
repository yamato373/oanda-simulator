package jp.yamato373.fix.price;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jp.yamato373.fix.util.Settings;
import lombok.Data;

@Service
public class RateGeneratWorker {

	private Rate rate;

	boolean downFlg;

	ExecutorService exec = Executors.newSingleThreadExecutor();

	@Autowired
	Settings settings;

	@PostConstruct
	public void init() {
		downFlg = true;
		rate = new Rate();
		rate.setAskPx(settings.getUpperLimit());
		rate.setBidPx(rate.getAskPx().subtract(settings.getSpread()));
		startGenerate();
	}

	private void startGenerate() {
		exec.execute(() -> {
			while (true) {

				if (downFlg) {
					rate.setAskPx(rate.getAskPx().subtract(settings.getMove()));
					rate.setBidPx(rate.getBidPx().subtract(settings.getMove()));
				} else {
					rate.setAskPx(rate.getAskPx().add(settings.getMove()));
					rate.setBidPx(rate.getBidPx().add(settings.getMove()));
				}

				if (settings.getUpperLimit().compareTo(rate.getAskPx()) <= 0) {
					downFlg = true;
				} else if (settings.getLowerLimit().compareTo(rate.getBidPx()) >= 0) {
					downFlg = false;
				}

				try {
					TimeUnit.MILLISECONDS.sleep(settings.getRateGenerateInterval());
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		});
	}

	public Rate getRate() {
		return rate;
	}

	@Data
	public static class Rate {

		BigDecimal bidPx;
		BigDecimal askPx;
	}
}
