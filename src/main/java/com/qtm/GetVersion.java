package com.qtm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.json.JSONObject;

public class GetVersion {
	
	public static void main(String[] args) throws Exception {
       
        getVersion();

    }

	private static String getVersion() throws IOException, InterruptedException {
		String url = "https://quantum.cloud.ibm.com/api/v1/versions";
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("Accept", "application/json")
				.GET().build();

		HttpClient client = HttpClient.newHttpClient();
		HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());
        System.out.println(json);
		return json.toString();
	}

}
