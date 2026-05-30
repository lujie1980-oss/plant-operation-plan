package com.plantops.api;

import com.plantops.sample.SampleDataLoader;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("/api/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    SampleDataLoader sampleDataLoader;

    @POST
    @Path("/reload-sample-data")
    public Response reloadSampleData(@QueryParam("dataset") String dataset,
                                     @QueryParam("resource") String resource) {
        String resolved = resolveResource(dataset, resource);
        sampleDataLoader.reloadDemo(resolved);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "OK");
        body.put("resource", resolved);
        body.put("message", "演示数据已重新加载");
        return Response.ok(body).build();
    }

    private static String resolveResource(String dataset, String resource) {
        if (resource != null && !resource.isBlank()) {
            return resource.trim();
        }
        if (dataset == null || dataset.isBlank()) {
            return "sample-data/factory-demo.json";
        }
        return switch (dataset.trim().toLowerCase()) {
            case "default", "factory-demo", "mahle" -> "sample-data/factory-demo.json";
            case "dunan", "dunan-full" -> "sample-data/factory-dunan-demo.json";
            case "dunan-lite" -> "sample-data/factory-dunan-demo-lite.json";
            default -> "sample-data/factory-demo.json";
        };
    }
}
