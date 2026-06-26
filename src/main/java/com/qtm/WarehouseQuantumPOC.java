package com.qtm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.json.JSONArray;
import org.json.JSONObject;

public class WarehouseQuantumPOC {

    // --- 1. Database connection ---
    public static Connection getConnection() throws SQLException {
        String url = "jdbc:mysql://test/promoerpmaster";
        String user = "test";
        String password = "test";
        return DriverManager.getConnection(url, user, password);
    }

    // --- 2. Fetch pick locations from SQL ---
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

    // --- 3. Obtain IBM Cloud IAM token ---
    public static String getIBMIAMToken(String apiKey) throws Exception {
        String url = "https://iam.cloud.ibm.com/identity/token";
        String body = "grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());
        return json.getString("access_token");
    }

    // --- 4. Submit TSP optimization job to IBM Qiskit Runtime ---
    public static String submitTSPJob(String token) throws Exception {
    	double[][] distanceMatrix = {
                {0, 5, 10, 8},
                {5, 0, 3, 7},
                {10, 3, 0, 2},
                {8, 7, 2, 0}
            };

            JSONArray matrixJson = new JSONArray();

            for (double[] row : distanceMatrix) {
                JSONArray rowJson = new JSONArray();
                for (double val : row) {
                    rowJson.put(val);
                }
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
        payload.put("program_id", "sampler");
        payload.put("backend", "ibm_fez");
        payload.put("params", params);
        System.out.println(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://quantum.cloud.ibm.com/api/v1/jobs"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("IBM-API-Version", "2025-05-01")
                .header("Service-CRN", "crn:v1:bluemix:public:quantum-computing:us-east:a/e9096ec3d4bd4c438df3c045993ecf6a:143010f1-ec76-4224-9683-ae20c39a4173::")
                .POST(BodyPublishers.ofString(payload.toString()))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        System.out.println(response.body());
        JSONObject respJson = new JSONObject(response.body());
        return respJson.getString("id"); // Job ID to track
    }

  

    // --- 6. Main execution ---
    public static void main(String[] args) throws Exception {
        int[] pickIds = {1, 2, 3, 4}; // Example pick list
        Connection conn = getConnection();
//        double[][] distanceMatrix = fetchLocations(conn, pickIds);
        conn.close();
        String apiKey = "";
        String token = getIBMIAMToken(apiKey);
        String jobId = submitTSPJob(token);
        System.out.println("Submitted job, ID: " + jobId);

    }

}
