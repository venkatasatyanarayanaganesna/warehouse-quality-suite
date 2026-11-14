package com.ganesna.wms.tests.api;

import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Race conditions on stock levels. Whatever order the requests arrive in, the final quantity
 * must equal the start plus the accepted receives minus the accepted picks, and the server must
 * never answer with a 5xx error.
 */
@Epic("API")
@Feature("Concurrency")
public class StockRaceApiTests {

    private String admin;

    @BeforeClass
    public void tokens() {
        admin = ApiClient.adminToken();
    }

    private static Map<String, Object> json(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    /** Runs all tasks at the same moment and returns their status codes in the order given. */
    private static List<Integer> runTogether(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            pool.shutdown();
        }
    }

    private Callable<Integer> receiveOne(String sku) {
        return () -> ApiClient.authed(admin).body(json("quantity", 1))
                .when().post("/api/items/" + sku + "/receive")
                .then().extract().statusCode();
    }

    private Callable<Integer> pickOne(String sku) {
        return () -> ApiClient.authed(admin).body(json("quantity", 1))
                .when().post("/api/items/" + sku + "/pick")
                .then().extract().statusCode();
    }

    private static int countOk(List<Integer> statuses) {
        int ok = 0;
        for (int status : statuses) {
            if (status == 200) {
                ok++;
            }
        }
        return ok;
    }

    private static void assertNoServerErrors(List<Integer> statuses) {
        for (int status : statuses) {
            Assert.assertTrue(status < 500, "a request ended in a server error: " + statuses);
        }
    }

    @Test(description = "Twenty simultaneous receives of one unit: no update is lost")
    public void parallelReceivesLoseNothing() throws Exception {
        String sku = ApiClient.createItem(admin, "Race receive", 10);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(receiveOne(sku));
        }
        List<Integer> statuses = runTogether(tasks);
        assertNoServerErrors(statuses);
        int accepted = countOk(statuses);
        Assert.assertTrue(accepted > 0, "no receive was accepted: " + statuses);
        Assert.assertEquals(ApiClient.quantityOf(admin, sku), 10 + accepted,
                "stock must equal the start plus every accepted receive");
    }

    @Test(description = "Receives and picks at the same moment keep the stock consistent and never negative")
    public void mixedReceivesAndPicksStayConsistent() throws Exception {
        String sku = ApiClient.createItem(admin, "Race mixed", 10);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            tasks.add(receiveOne(sku));
        }
        for (int i = 0; i < 15; i++) {
            tasks.add(pickOne(sku));
        }
        List<Integer> statuses = runTogether(tasks);
        assertNoServerErrors(statuses);
        int receivesOk = countOk(statuses.subList(0, 15));
        int picksOk = countOk(statuses.subList(15, 30));
        int finalQuantity = ApiClient.quantityOf(admin, sku);
        Assert.assertTrue(finalQuantity >= 0, "stock went negative: " + finalQuantity);
        Assert.assertEquals(finalQuantity, 10 + receivesOk - picksOk,
                "stock must equal start + accepted receives - accepted picks");
    }
}
