package com.google.glassware;

import com.google.api.services.mirror.model.Location;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

public class LocationUtil {
	private static final Logger LOG = Logger.getLogger(MainServlet.class.getSimpleName());

	private static final String KIND = LocationUtil.class.getName();

	public static void save(String userId, Location location) {
		LOG.info("Saved location for " + userId);

		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Entity entity = new Entity(KIND, userId);
		entity.setProperty("userId", userId);
		entity.setProperty("latitude", location.getLatitude());
		entity.setProperty("longitude", location.getLongitude());
		Date date = new Date();
		entity.setProperty("date", date);	// GMT

		datastore.put(entity);
	}

	public static Location get(String userId) {
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Key key = KeyFactory.createKey(KIND, userId);
		try {
			Entity entity = datastore.get(key);
			Date storedDate = (Date) entity.getProperty("date");
			/*
			DateFormat formatter;
			// 2013-06-30 20:04:27.322000
			formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Date date = formatter.parse(storedDate);
			Date now = new Date();
			LOG.info("date " + date + " : " + now);
			*/
			LOG.info("storedDate " + storedDate);
			Location location = new Location();
			location.setLatitude((Double) entity.getProperty("latitude"));
			location.setLongitude((Double) entity.getProperty("longitude"));
			return location;
		} catch (EntityNotFoundException e) {
			return null;
		}
	}
}
