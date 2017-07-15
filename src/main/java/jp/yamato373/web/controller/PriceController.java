package jp.yamato373.web.controller;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jp.yamato373.fix.util.Settings;

@RestController
public class PriceController {

	@Autowired
	Settings settings;

	@RequestMapping(value = "/price/settings", method = RequestMethod.GET)
	public Settings getSettings(
			@RequestParam("sendInterval") Optional<Integer> sendInterval,
			@RequestParam("upperLimit") Optional<BigDecimal> upperLimit,
			@RequestParam("lowerLimit") Optional<BigDecimal> lowerLimit,
			@RequestParam("spread") Optional<BigDecimal> spread,
			@RequestParam("move") Optional<BigDecimal> move,
			@RequestParam("rateGenerateInterval") Optional<Long> rateGenerateInterval) {

		sendInterval.ifPresent(c -> settings.setSendInterval(sendInterval.get()));
		upperLimit.ifPresent(c -> settings.setUpperLimit(upperLimit.get()));
		lowerLimit.ifPresent(c -> settings.setLowerLimit(lowerLimit.get()));
		spread.ifPresent(c -> settings.setSpread(spread.get()));
		move.ifPresent(c -> settings.setMove(move.get()));
		rateGenerateInterval.ifPresent(c -> settings.setRateGenerateInterval(rateGenerateInterval.get()));

		return settings;
	}
}