/*
 * Copyright (C) 2013 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.glassware;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.mirror.Mirror;
import com.google.api.services.mirror.model.Location;
import com.google.api.services.mirror.model.Notification;
import com.google.api.services.mirror.model.NotificationConfig;
import com.google.api.services.mirror.model.TimelineItem;
import com.google.api.services.mirror.model.UserAction;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the notifications sent back from subscriptions
 * 
 * @author Jenny Murphy - http://google.com/+JennyMurphy
 */
@SuppressWarnings("serial")
public class NotifyServlet extends HttpServlet {
	private static final Logger LOG = Logger.getLogger(MainServlet.class.getSimpleName());

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// Respond with OK and status 200 in a timely fashion to prevent
		// redelivery
		response.setContentType("text/html");
		Writer writer = response.getWriter();
		writer.append("OK");
		writer.close();

		// Get the notification object from the request body (into a string so
		// we
		// can log it)
		BufferedReader notificationReader = new BufferedReader(new InputStreamReader(request.getInputStream()));
		String notificationString = "";

		// Count the lines as a very basic way to prevent Denial of Service
		// attacks
		int lines = 0;
		while (notificationReader.ready()) {
			notificationString += notificationReader.readLine();
			lines++;

			// No notification would ever be this long. Something is very wrong.
			if (lines > 1000) {
				throw new IOException("Attempted to parse notification payload that was unexpectedly long.");
			}
		}

		LOG.info("got raw notification " + notificationString);

		JsonFactory jsonFactory = new JacksonFactory();

		// If logging the payload is not as important, use
		// jacksonFactory.fromInputStream instead.
		Notification notification = jsonFactory.fromString(notificationString, Notification.class);

		LOG.info("Got a notification with ID: " + notification.getItemId());

		// Figure out the impacted user and get their credentials for API calls
		String userId = notification.getUserToken();
		Credential credential = AuthUtil.getCredential(userId);
		Mirror mirrorClient = MirrorClient.getMirror(credential);

		if (notification.getCollection().equals("locations")) {
			LOG.info("Notification of updated location");
			Mirror glass = MirrorClient.getMirror(credential);
			// item id is usually 'latest'
			Location location = glass.locations().get(notification.getItemId()).execute();

			LOG.info("New location is " + location.getLatitude() + ", " + location.getLongitude());
			// MirrorClient.insertTimelineItem(
			// credential,
			// new TimelineItem().setText("You are now at " +
			// location.getLatitude() + ", " + location.getLongitude())
			// .setNotification(new
			// NotificationConfig().setLevel("DEFAULT")).setLocation(location)
			// .setMenuItems(Lists.newArrayList(new
			// MenuItem().setAction("NAVIGATE"))));
			LocationUtil.save(userId, location);
			// This is a location notification. Ping the device with a timeline
			// item
			// telling them where they are.
		} else if (notification.getCollection().equals("timeline")) {
			// Get the impacted timeline item
			TimelineItem timelineItem = mirrorClient.timeline().get(notification.getItemId()).execute();
			LOG.info("Notification impacted timeline item with ID: " + timelineItem.getId());

			// If it was a share, and contains a photo, bounce it back to the
			// user.
			if (notification.getUserActions().contains(new UserAction().setType("SHARE")) && timelineItem.getAttachments() != null
					&& timelineItem.getAttachments().size() > 0) {
				LOG.info("It was a share of a photo. Sending the photo back to the user.");

				// Get the first attachment
				String attachmentId = timelineItem.getAttachments().get(0).getId();
				LOG.info("Found attachment with ID " + attachmentId);

				// Get the attachment content
				InputStream stream = MirrorClient.getAttachmentInputStream(credential, timelineItem.getId(), attachmentId);

				// Create a new timeline item with the attachment
				TimelineItem echoPhotoItem = new TimelineItem();
				echoPhotoItem.setNotification(new NotificationConfig().setLevel("DEFAULT"));
				echoPhotoItem.setText("Echoing your shared photo");

				MirrorClient.insertTimelineItem(credential, echoPhotoItem, "image/jpeg", stream);

			}
			if (notification.getUserActions().contains(new UserAction().setType("CUSTOM").setPayload("athome"))) {
				LOG.info("custom at home");

				// Mirror glass = MirrorClient.getMirror(credential);

				Location location = LocationUtil.get(userId);
				if (location!=null) {
					LOG.info("got location");
				} else {
					LOG.info("missing location");
				}
			}
			if (notification.getUserActions().contains(new UserAction().setType("CUSTOM").setPayload("drill"))) {
				LOG.info("custom drill");
				TimelineItem drillItem = new TimelineItem();
				drillItem.setText("Drill, baby drill!");
				drillItem.setNotification(new NotificationConfig().setLevel("DEFAULT"));

				try {
					URL url = new URL("http://nazret.com/blog/media/blogs/new/oil_drill2042909.jpg");
					URLConnection connection1 = url.openConnection();

					MirrorClient.insertTimelineItem(credential, drillItem, "image/jpeg", connection1.getInputStream());
				} catch (Exception e) {
					LOG.info("Couldn't get URL");
					MirrorClient.insertTimelineItem(credential, drillItem);
				}

				Key drilledKey = KeyFactory.createKey("Drilling", "dont know");
				Date date = new Date();
				Entity drilled = new Entity("drilled", drilledKey);
				drilled.setProperty("userId", userId);
				drilled.setProperty("date", date);
				drilled.setProperty("timelineId", timelineItem.getId());

				DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
				datastore.put(drilled);

			} else {
				LOG.warning("I don't know what to do with this notification, so I'm ignoring it." + notification.getUserActions());
			}
		}
	}
}
