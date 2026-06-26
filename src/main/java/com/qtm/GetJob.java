package com.qtm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.json.JSONObject;

public class GetJob {
	public static void main(String[] args) throws Exception {

		String apiKey = "";
		String token = getIBMIAMToken(apiKey);
		JSONObject result = getJobResult(token, "");
		System.out.println("Optimized picker route: " + result.toString());
	}

	public static String getIBMIAMToken(String apiKey) throws Exception {
		String url = "https://iam.cloud.ibm.com/identity/token";
		String body = "grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=" + apiKey;

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
				.header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
				.POST(BodyPublishers.ofString(body)).build();

		HttpClient client = HttpClient.newHttpClient();
		HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
		JSONObject json = new JSONObject(response.body());
		return json.getString("access_token");
	}

	public static JSONObject getJobResult(String token, String jobId) throws Exception {
		String url = "https://quantum.cloud.ibm.com/api/v1/jobs/" + jobId;
		HttpClient client = HttpClient.newHttpClient();
		while (true) {
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
					.header("Authorization", "Bearer " + token).header("IBM-API-Version", "2025-05-01")
					.header("Service-CRN",
							"crn:v1:bluemix:public:quantum-computing:us-east:a/e9096ec3d4bd4c438df3c045993ecf6a:143010f1-ec76-4224-9683-ae20c39a4173::")
					.GET().build();
			HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
			System.out.println(response.body());
			JSONObject json = new JSONObject(response.body());
			String status = json.getString("status");
			if (status.equalsIgnoreCase("COMPLETED") || status.equalsIgnoreCase("DONE")) {
				return json.getJSONObject("result"); // Contains optimized route
			} else if (status.equalsIgnoreCase("FAILED")) {
				throw new RuntimeException("Quantum job failed: " + json.toString());
			}
			System.out.println("Job status: " + status + ", waiting...");
			Thread.sleep(5000); // Poll every 5 seconds
		}
	}
}
