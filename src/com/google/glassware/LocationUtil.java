package com.google.glassware;

import com.google.api.services.mirror.model.Location;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.javadocmd.simplelatlng.LatLng;
import com.javadocmd.simplelatlng.LatLngTool;
import com.javadocmd.simplelatlng.util.LengthUnit;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class LocationUtil {
	private static final Logger LOG = Logger.getLogger(MainServlet.class.getSimpleName());

	private static final String KIND = LocationUtil.class.getName();
	private static final String LOCATION_CURRENT = KIND + ".current";
	private static final String LOCATION_TAGS = KIND + ".tags";

	/**
	 * Save the glass wearer's current location.
	 * 
	 * @param userId
	 * @param location
	 */
	public static void save(String userId, Location location) {

		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Entity entity = new Entity(LOCATION_CURRENT, userId);
		entity.setProperty("userId", userId);
		entity.setProperty("latitude", location.getLatitude());
		entity.setProperty("longitude", location.getLongitude());
		Date date = new Date();
		entity.setProperty("date", date); // GMT

		datastore.put(entity);
		LOG.info("Saved location for " + userId);
	}

	public static void saveTag(String userId, Location location, String tag) {

		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Entity entity = new Entity(LOCATION_TAGS, userId + tag);
		entity.setProperty("userId", userId);
		entity.setProperty("latitude", location.getLatitude());
		entity.setProperty("longitude", location.getLongitude());
		Date date = new Date();
		entity.setProperty("date", date); // GMT
		entity.setProperty("tag", tag);

		datastore.put(entity);
		LOG.info("Saved location for " + userId + " tag " + tag);
	}

	public static Location get(String userId) {
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Key key = KeyFactory.createKey(LOCATION_CURRENT, userId);
		try {
			Entity entity = datastore.get(key);
			Date storedDate = (Date) entity.getProperty("date");

			LOG.info("storedDate " + storedDate);
			Long storedTime = storedDate.getTime();
			Date now = new Date();
			Long currentTime = now.getTime();
			// determine how old the stored entry is by seconds
			Long timeDifference = (currentTime - storedTime) / 1000;
			LOG.info("timeDifference = " + Long.valueOf(timeDifference));

			if (timeDifference > 60 * 15) {
				// if older than 15 minutes than ignore old location data
				return null;
			}
			Location location = new Location();
			location.setLatitude((Double) entity.getProperty("latitude"));
			location.setLongitude((Double) entity.getProperty("longitude"));
			return location;
		} catch (EntityNotFoundException e) {
			return null;
		}
	}

	public static Location getTag(String userId, String tag) {
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Key key = KeyFactory.createKey(LOCATION_TAGS, userId + tag);

		try {
			Entity entity = datastore.get(key);

			Location location = new Location();
			location.setLatitude((Double) entity.getProperty("latitude"));
			location.setLongitude((Double) entity.getProperty("longitude"));
			return location;
		} catch (EntityNotFoundException e) {
			return null;
		}

	}

	public static List<LocationTag> getAllTags(String userId) {
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

		Filter userIdFilter = new FilterPredicate("userId", FilterOperator.EQUAL, userId);
		Query tagQuery = new Query(LOCATION_TAGS).setFilter(userIdFilter);
		Iterable<Entity> userEntities = datastore.prepare(tagQuery).asIterable();

		List<LocationTag> tags = new ArrayList<LocationTag>();
		for (Entity tagEntity : userEntities) {
			LOG.info("found: " + userId + " " + tagEntity.getProperty("tag"));
			Location location = new Location();
			location.setLatitude((Double) tagEntity.getProperty("latitude"));
			location.setLongitude((Double) tagEntity.getProperty("longitude"));

			LocationTag tag = new LocationTag();
			tag.setUserId(userId);
			tag.setLocation(location);
			tag.setTag((String) tagEntity.getProperty("tag"));

			tags.add(tag);
		}
		return tags;
	}

	public static double distanceBetweenLocations(Location location1, Location location2) {
		LatLng point1 = new LatLng(location1.getLatitude(), location1.getLongitude());
		LatLng point2 = new LatLng(location2.getLatitude(), location2.getLongitude());
		double distanceInMiles = LatLngTool.distance(point1, point2, LengthUnit.MILE);
		return distanceInMiles;
	}

	public static String enterTag(String userId, Location previous, Location current) {
		List<LocationTag> locationTags = getAllTags(userId);
		if (locationTags == null) {
			// no tags for this user
			return null;
		}
		if (distanceBetweenLocations(previous, current) < 0.1) {
			LOG.info("not enough distance between previous and current");
//			return null;
		}
		for (LocationTag locationTag : locationTags) {
			if (distanceBetweenLocations(locationTag.getLocation(), current) < 0.1) {
				LOG.info("matched tag location: "+locationTag.getTag());
				return locationTag.getTag();
			}
		}
		LOG.info("no matched tag");
		return null;
	}
}
