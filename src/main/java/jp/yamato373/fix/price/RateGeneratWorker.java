package jp.yamato373.fix.price;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import lombok.Data;

@Service
public class RateGeneratWorker {

	private static final BigDecimal UPPER_LIMIT = new BigDecimal("110");
	private static final BigDecimal LOWER_LIMIT = new BigDecimal("90");
	private static final BigDecimal SPREAD = new BigDecimal("0.004");
	private static final BigDecimal MOVE = new BigDecimal("0.001");
	private static final int INTERVAL = 100;

	private Rate rate;

	boolean downFlg;

	ExecutorService exec = Executors.newSingleThreadExecutor();


	@PostConstruct
	public void init() {
		downFlg = true;
		rate = new Rate();
		rate.setAskPx(UPPER_LIMIT);
		rate.setBidPx(rate.getAskPx().subtract(SPREAD));
		startGenerate();
	}

	private void startGenerate() {
		exec.execute(() -> {
			while (true) {

				if (downFlg) {
					rate.setAskPx(rate.getAskPx().subtract(MOVE));
					rate.setBidPx(rate.getBidPx().subtract(MOVE));
				} else {
					rate.setAskPx(rate.getAskPx().add(MOVE));
					rate.setBidPx(rate.getBidPx().add(MOVE));
				}

				if (UPPER_LIMIT.compareTo(rate.getAskPx()) <= 0) {
					downFlg = true;
				} else if (LOWER_LIMIT.compareTo(rate.getBidPx()) >= 0) {
					downFlg = false;
				}

				try {
					TimeUnit.MILLISECONDS.sleep(INTERVAL);
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
