package com.fafe.controller;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;

import com.fafe.core.StockMarket;
import com.fafe.core.properties.ConfigLoader;
import com.fafe.core.properties.ConfigProperty;
import com.fafe.service.MailService;
import com.fafe.trade.core.mongodb.MongoDAO;
import com.fafe.trade.python_caller.PhytonCaller;
import com.nhefner.main.StockFetcher;

/**
 * Hello world!
 *
 */
public class Routine {
	public static Logger log = Logger.getLogger(Routine.class);

	public static SimpleDateFormat sdfddMMyyyyHHmm = new SimpleDateFormat("dd/MM/yyyy HH:mm");
	// private static String yahooDataFolder =
	// "C://Python27//Lib//site-packages//QSTK//QSData//";

	private static String pythonFolder = null;
	private static ConfigLoader cl = null;
	static {
		try {
			cl = new ConfigLoader();
			pythonFolder = cl.getStrProperty(ConfigProperty.PYTHON_DATA_PATH);
		} catch (ConfigurationException e) {
			error("Could not initialize ", e);
		}
	}

	public static void execute(int days, boolean dryRun, String stockMarket) {

		log.info("********************************************");
		log.info("Start at " + sdfddMMyyyyHHmm.format(new Date(System.currentTimeMillis())));

		MongoDAO mongoDao = null;
		MailService mailService = null;
		try {
			mailService = new MailService();
		} catch (ConfigurationException e2) {
			error("Error while creating mail srvice", e2);
		}
		Date currentDate = null;
		try {
			mongoDao = new MongoDAO(StockMarket.getStockMarketFromAcr(stockMarket));

			if (days != 0) {
				currentDate = mongoDao.getAdjustedDate(days);
				log.info("Date " + days + " : " + MongoDAO.sdf.format(currentDate));
			} else {
				currentDate = mongoDao.getCurrentDate();
				log.info("Current date: " + MongoDAO.sdf.format(currentDate));
			}

		} catch (UnknownHostException e) {
			error("Error with Mongo service", e);
		} catch (Exception e) {
			error("Error while getting the current date", e);
		}

		StockFetcher sf = null;
		try {
			sf = new StockFetcher(stockMarket);
		} catch (ConfigurationException e1) {
			error("StockFetcher failed", e1);
		}

		List<String> upToDateSymbolList = new LinkedList<String>();
		List<String> outOfDateSymbolList = new LinkedList<String>();
		boolean tryAgain = false;
		long startTime = System.currentTimeMillis();
		try {
			do {
				if (tryAgain) {
					if (System.currentTimeMillis() - startTime > 32400000) {
						error("Timeout occured after 6 hours", new InterruptedException());
					}
					log.info("Waiting 10 mins before fecthing yahoo data...");
					Thread.sleep(600000);
				}

				try {
					sf.getYahooData();
					log.info("Yahoo data fetched");
				} catch (IOException e) {
					error("Error while fetching yahoo data", e);
				}

				sf.validateNewData(currentDate, upToDateSymbolList, outOfDateSymbolList);
				tryAgain = true;
			} while (upToDateSymbolList.size() == 0);
		} catch (Exception e) {
			error("While cheking yahoo data", e);
		}
		log.info("Yahoo data validate!");

		log.warn("Symbols out of date: ");
		for (String symbol : outOfDateSymbolList) {
			log.warn(symbol);
		}

		SimpleDateFormat yyyy = new SimpleDateFormat("yyyy");
		SimpleDateFormat MM = new SimpleDateFormat("MM");
		SimpleDateFormat dd = new SimpleDateFormat("dd");
		String[] params = { yyyy.format(currentDate), MM.format(currentDate), dd.format(currentDate), stockMarket };
		PhytonCaller pc = new PhytonCaller(pythonFolder, "analysis.py", params);
		try {
			pc.call();
		} catch (Exception e) {
			error("Error while calling analysis script", e);
		}
		log.info("Analysis done!");

		String[] orderParams = { stockMarket, cl.getStrProperty(ConfigProperty.BUY_IND),
				cl.getStrProperty(ConfigProperty.BB_THRESHOLD), cl.getStrProperty(ConfigProperty.SELL_OFFSET) };
		pc = new PhytonCaller(pythonFolder, "order_generator.py", orderParams);
		try {
			pc.call();
		} catch (Exception e) {
			error("Error while calling the order generator", e);
		}
		log.info("Order generated!");

		// read order
		String mailBody = "";
		mailBody = mailBody.concat(getOrdersToString(pythonFolder + stockMarket + "/analysis/"));

		mailBody = mailBody.concat("<br>Stock out of date:<br>");
		for (String symbol : outOfDateSymbolList) {
			mailBody = mailBody.concat(symbol + "<br>");
		}

		mailBody = mailBody.concat("<br>Working date: " + MongoDAO.sdf.format(currentDate));

		String dryRunMsg = "<br>Dry run: " + dryRun;
		if (!dryRun) {
			try {
				if (mongoDao.getCurrentDate().before(new Date(System.currentTimeMillis()))) {
					mongoDao.updateDate();
				} else {
					dryRunMsg = dryRunMsg.concat(" (Wrk date unchanged)");
				}

			} catch (Exception e) {
				error("Error while updating the current date", e);
			}
		}

		mailBody = mailBody.concat(dryRunMsg);

		// sending mail
		mailService.sendMessage(cl.getStrProperty(ConfigProperty.EMAILTO),
				stockMarket + " - Order generated on " + sdfddMMyyyyHHmm.format(new Date(System.currentTimeMillis())),
				mailBody);

	}

	private static String getOrdersToString(String orderPath) {

		String lineSeparator = "<br>";// System.getProperty("line.separator");
		String orderFile = orderPath + "orderFromEvent.csv";
		String ret = lineSeparator;
		String temp = null;
		boolean noOrder = true;
		try (BufferedReader br = new BufferedReader(new FileReader(orderFile))) {
			while ((temp = br.readLine()) != null) {
				ret = ret.concat(temp + lineSeparator);
				noOrder = false;
			}
		} catch (IOException e) {
			error("Error while reading " + orderFile, e);
		}
		if (noOrder) {
			ret = ret.concat("No order generated" + lineSeparator);
		}

		return ret;
	}

	private static void error(String error, Exception e) {
		log.error(error, e);
		System.exit(1);
	}

	public static void main(String[] args) {
		// Routine.execute(-24, true);
		// Routine.execute(-23, true);
		// Routine.execute(-22, true);
		// Routine.execute(-21, true);
		// Routine.execute(-20, true);
		// Routine.execute(-19, true);
		// Routine.execute(-18, true);
		// Routine.execute(-17, true);
		// Routine.execute(-16, true);
		// Routine.execute(-15, true);
		// Routine.execute(-14, true);
		// Routine.execute(-13, true);
		// Routine.execute(-12, true);
		// Routine.execute(-11, true);
		// Routine.execute(-10, true);
		// Routine.execute(-9, true);
		// Routine.execute(-8, true);
		// Routine.execute(-7, true);
		// Routine.execute(-6, true);
		// Routine.execute(-5, true);
		// Routine.execute(-4, true);
		// Routine.execute(-3, true);
		// Routine.execute(-2, true);
		// Routine.execute(-1, true);
		// Routine.execute(0, true);
		// Routine.execute(-1, true, "PA");
		Routine.execute(Integer.parseInt(args[0]), Boolean.parseBoolean(args[1]), args[2]);
		// Routine.execute(-1, true, "NYSE");
	}
}
