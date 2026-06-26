package com.qtm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.json.JSONArray;
import org.json.JSONObject;

public class IBMQuantumSimulatorPOC {

    public static void main(String[] args) throws Exception {
    	 int[] pickIds = {1, 2, 3, 4}; // Example pick list
         Connection conn = getConnection();
         double[][] distanceMatrix = fetchLocations(conn, pickIds);
         conn.close();
        // Step 1️⃣ - Your IBM Cloud API key (not the IBM Quantum platform token)
        String apiKey = "5KecaRUFEf8va8KCkkIPGZgWIJq4KLh5vMUs6Th4yRgD";

        // Step 2️⃣ - Get IAM access token
        String iamTokenEndpoint = "https://iam.cloud.ibm.com/identity/token";
        String formData = "grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=" + apiKey;

        HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create(iamTokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());

        if (tokenResponse.statusCode() != 200) {
            System.out.println("❌ Failed to get IAM token: " + tokenResponse.body());
            return;
        }

        JSONObject tokenJson = new JSONObject(tokenResponse.body());
        String iamToken = tokenJson.getString("access_token");
        System.out.println("✅ IAM Token acquired.");

        // Step 3️⃣ - IBM Quantum Runtime endpoint
        String endpoint = "https://quantum.cloud.ibm.com/api/v1/jobs";

        // Step 4️⃣ - Build simple QASM job payload
        JSONObject params = submitTSPJob(distanceMatrix);
        JSONObject payload = new JSONObject()
                .put("program_id", "sampler")
                .put("backend", "ibmq_qasm_simulator")
                .put("params", params);

        // Step 5️⃣ - Your Quantum Service CRN
        String serviceCRN = "crn:v1:bluemix:public:quantum-computing:us-east:a/e9096ec3d4bd4c438df3c045993ecf6a:143010f1-ec76-4224-9683-ae20c39a4173::";

        // Step 6️⃣ - Submit job
        HttpRequest jobRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + iamToken)
                .header("Service-CRN", serviceCRN)
                .header("IBM-API-Version", "2025-05-01")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> jobResponse = client.send(jobRequest, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status: " + jobResponse.statusCode());
        System.out.println("Response: " + jobResponse.body());
    }
    
    public static Connection getConnection() throws SQLException {
        String url = "jdbc:mysql://192.168.10.13/promoerpmaster";
        String user = "test";
        String password = "C@$1n0@123";
        return DriverManager.getConnection(url, user, password);
    }
    public static double[][] fetchLocations(Connection conn, int[] pickIds) throws SQLException {
        String sql = "SELECT location_x, location_y FROM location_coordinates WHERE Stock_Location_Id IN (" +
                     String.join(",", java.util.Arrays.stream(pickIds).mapToObj(String::valueOf).toArray(String[]::new)) + ")";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

        java.util.List<double[]> coords = new java.util.ArrayList<>();
        while (rs.next()) {
            double x = rs.getDouble("location_x");
            double y = rs.getDouble("location_y");
            coords.add(new double[]{x, y});
        }

        // Generate distance matrix
        int n = coords.size();
        double[][] distanceMatrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) distanceMatrix[i][j] = 0;
                else {
                    double dx = coords.get(i)[0] - coords.get(j)[0];
                    double dy = coords.get(i)[1] - coords.get(j)[1];
                    distanceMatrix[i][j] = Math.sqrt(dx * dx + dy * dy); // Euclidean distance
                }
            }
        }
        return distanceMatrix;
    }
    
    public static JSONObject submitTSPJob(double[][] distanceMatrix) throws Exception {
        JSONArray matrixJson = new JSONArray();
        for (double[] row : distanceMatrix) {
            JSONArray rowJson = new JSONArray();
            for (double val : row) rowJson.put(val);
            matrixJson.put(rowJson);
        }

        JSONObject problem = new JSONObject();
        problem.put("name", "TSP_picker_route");
        problem.put("type", "tsp");
        problem.put("distance_matrix", matrixJson);

        JSONObject params = new JSONObject();
        params.put("problem", problem);
        params.put("algorithm", "qaoa");
        params.put("shots", 1024);

        JSONObject payload = new JSONObject();
        payload.put("program_id", "optimization");
        payload.put("backend", "ibmq_qasm_simulator");
        payload.put("params", params);
        System.out.println(payload);

       return payload;
    }
}


