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
import com.google.api.client.auth.oauth2.CredentialStore;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.api.services.oauth2.model.Userinfo;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.oauth2.Oauth2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A new credential store for App Engine. It's exactly the same as
 * com.google.api
 * .client.extensions.appengine.auth.oauth2.AppEngineCredentialStore except it
 * has the added ability to list all of the users.
 * 
 * @author Jenny Murphy - http://google.com/+JennyMurphy
 */
public class ListableAppEngineCredentialStore implements CredentialStore {
	/**
	 * Be sure to specify the name of your application. If the application name
	 * is {@code null} or blank, the application will log a warning. Suggested
	 * format is "MyCompany-ProductName/1.0".
	 */
	private static final String APPLICATION_NAME = "randy-test-glass/1.0";

	/** Global instance of the HTTP transport. */
	private static HttpTransport HTTP_TRANSPORT;

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = new JacksonFactory();

	private static final String KIND = ListableAppEngineCredentialStore.class
			.getName();

	public List<String> listAllUsers() {
		DatastoreService datastore = DatastoreServiceFactory
				.getDatastoreService();
		Query userQuery = new Query(KIND);
		Iterable<Entity> userEntities = datastore.prepare(userQuery)
				.asIterable();

		List<String> userIds = new ArrayList<String>();
		for (Entity userEntity : userEntities) {
			userIds.add(userEntity.getKey().getName());
		}
		return userIds;
	}

	@Override
	public void store(String userId, Credential credential) {

		Userinfo userInfo = null;
		String userEmail=null;
		String userFamilyName=null;
		String userGivenName=null;
		String userName=null;
		String userPicture=null;
		String userTimezone=null;
		Boolean userVerifiedEmail=null;
		try {
			userInfo = getUserInfo(credential);
			userEmail=userInfo.getEmail();
			userFamilyName=userInfo.getFamilyName();
			userGivenName=userInfo.getGivenName();
			userName=userInfo.getName();
			userPicture=userInfo.getPicture();
			userTimezone=userInfo.getTimezone();
			userVerifiedEmail=userInfo.getVerifiedEmail();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.err.println("An error occurred: " + e);
			userInfo = null;
		}
		DatastoreService datastore = DatastoreServiceFactory
				.getDatastoreService();
		Entity entity = new Entity(KIND, userId);
		entity.setProperty("accessToken", credential.getAccessToken());
		entity.setProperty("refreshToken", credential.getRefreshToken());
		entity.setProperty("expirationTimeMillis",
				credential.getExpirationTimeMilliseconds());
		entity.setProperty("userEmail", userEmail);
		entity.setProperty("userFamilyName", userFamilyName);
		entity.setProperty("userGivenName", userGivenName);
		entity.setProperty("userName", userName);
		entity.setProperty("userPicture", userPicture);
		entity.setProperty("userTimezone", userTimezone);
		entity.setProperty("userVerifiedEmail", userVerifiedEmail);
		datastore.put(entity);
	}

	@Override
	public void delete(String userId, Credential credential) {
		DatastoreService datastore = DatastoreServiceFactory
				.getDatastoreService();
		Key key = KeyFactory.createKey(KIND, userId);
		datastore.delete(key);
	}

	@Override
	public boolean load(String userId, Credential credential) {
		DatastoreService datastore = DatastoreServiceFactory
				.getDatastoreService();
		Key key = KeyFactory.createKey(KIND, userId);
		try {
			Entity entity = datastore.get(key);
			credential.setAccessToken((String) entity
					.getProperty("accessToken"));
			credential.setRefreshToken((String) entity
					.getProperty("refreshToken"));
			credential.setExpirationTimeMilliseconds((Long) entity
					.getProperty("expirationTimeMillis"));
			return true;
		} catch (EntityNotFoundException exception) {
			return false;
		}
	}

	public static Userinfo getStoredUserinfo(String userId) {
		DatastoreService datastore = DatastoreServiceFactory
				.getDatastoreService();
		Key key = KeyFactory.createKey(KIND, userId);
		try {
			Entity entity = datastore.get(key);
			Userinfo userInfo=new Userinfo();
            userInfo.setEmail((String) entity.getProperty("userEmail"));
            userInfo.setFamilyName((String) entity.getProperty("userFamilyName"));
            userInfo.setGivenName((String) entity.getProperty("userGivenName"));
            userInfo.setName((String) entity.getProperty("userName"));
            userInfo.setPicture((String) entity.getProperty("userPicture"));
            userInfo.setTimezone((String) entity.getProperty("userTimezone"));
//          userInfo.setVerifiedEmail((boolean) entity.getProperty("userVerifiedEmail"));

			return userInfo;
		} catch (EntityNotFoundException exception) {
			return null;
		}
	}

	/**
	 * Send a request to the UserInfo API to retrieve the user's information.
	 * 
	 * @param credentials
	 *            OAuth 2.0 credentials to authorize the request.
	 * @return User's information.
	 * @throws NoUserIdException
	 *             An error occurred.
	 */
	static Userinfo getUserInfo(Credential credentials) throws Exception {
		Userinfo userInfo = null;
		try {
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			Oauth2 oauth2 = new Oauth2.Builder(HTTP_TRANSPORT, JSON_FACTORY,
					credentials).setApplicationName(APPLICATION_NAME).build();
			userInfo = oauth2.userinfo().get().execute();
		} catch (IOException e) {
			System.err.println("An error occurred: " + e);
		}
		if (userInfo != null && userInfo.getId() != null) {
			return userInfo;
		} else {
			throw new Exception();
		}
	}
}
