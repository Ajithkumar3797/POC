package com.qtm;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.json.JSONObject;

public class QtmWarehousePOC {

    public static void main(String[] args) throws Exception {

        String apiKey = "5KecaRUFEf8va8KCkkIPGZgWIJq4KLh5vMUs6Th4yRgD"; // IBM Quantum API Key
        String serviceCrn = "crn:v1:bluemix:public:quantum-computing:us-east:a/e9096ec3d4bd4c438df3c045993ecf6a:143010f1-ec76-4224-9683-ae20c39a4173::"; // Quantum Runtime CRN
        String backend = "ibm_fez"; // or ibmq_qasm_simulator

        // 1️⃣ Step: Get IAM Access Token
        HttpClient client = HttpClient.newHttpClient();
        String iamBody = "grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=" + apiKey;

        HttpRequest iamRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://iam.cloud.ibm.com/identity/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(iamBody))
                .build();

        HttpResponse<String> iamResponse = client.send(iamRequest, HttpResponse.BodyHandlers.ofString());
        JSONObject iamJson = new JSONObject(iamResponse.body());
        String accessToken = iamJson.getString("access_token");
        System.out.println("✅ IAM Access Token acquired.");

        // 2️⃣ Step: Define distance matrix for 8 warehouse locations
        double[][] distanceMatrix = {
        		{0, 5, 10, 8},
                {5, 0, 3, 7},
                {10, 3, 0, 2},
                {8, 7, 2, 0}
        };

        JSONObject problem = new JSONObject();
        problem.put("name", "TSP_picker_route");
        problem.put("type", "tsp");
        problem.put("distance_matrix", distanceMatrix);

        JSONObject params = new JSONObject();
        params.put("problem", problem);
        params.put("algorithm", "qaoa");
        params.put("shots", 1024);
        params.put("reps", 2);
        
        // 3️⃣ Step: Build JSON payload for QAOA runtime program
        JSONObject payload = new JSONObject();
        payload.put("program_id", "sampler"); // IBM QAOA program
        payload.put("backend", backend);
        payload.put("params", params);

        // Input: distance matrix
        JSONObject input = new JSONObject();
//        input.put("distance_matrix", distanceMatrix);
//        input.put("reps", 1); // QAOA reps
//        payload.put("input", input);
        System.out.println(payload);
        // 4️⃣ Step: Submit job to IBM Quantum Runtime
        HttpRequest jobRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://quantum.cloud.ibm.com/api/v1/jobs"))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("Service-CRN", serviceCrn)
                .header("IBM-API-Version", "2025-05-01")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> jobResponse = client.send(jobRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println(jobResponse.body());
        JSONObject jobJson = new JSONObject(jobResponse.body());
        String jobId = jobJson.getString("id");
        System.out.println("✅ Job submitted. Job ID: " + jobId);

        // 5️⃣ Step: Poll for job completion
        String status = "";
        while (!status.equalsIgnoreCase("COMPLETED") && !status.equalsIgnoreCase("FAILED")) {
            Thread.sleep(5000); // wait 5 seconds
            HttpRequest statusRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://quantum.cloud.ibm.com/api/v1/jobs/" + jobId))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Service-CRN", serviceCrn)
                    .header("IBM-API-Version", "2025-05-01")
                    .GET()
                    .build();

            HttpResponse<String> statusResponse = client.send(statusRequest, HttpResponse.BodyHandlers.ofString());
            JSONObject statusJson = new JSONObject(statusResponse.body());
            status = statusJson.getString("status");
            System.out.println("Job status: " + status);

            if (status.equalsIgnoreCase("COMPLETED")) {
                JSONObject result = statusJson.getJSONObject("result");
                System.out.println("✅ Job completed. Result: " + result.toString());
                break;
            } else if (status.equalsIgnoreCase("FAILED")) {
                System.err.println("❌ Job failed: " + statusJson.toString());
                break;
            }
        }
    }
}
