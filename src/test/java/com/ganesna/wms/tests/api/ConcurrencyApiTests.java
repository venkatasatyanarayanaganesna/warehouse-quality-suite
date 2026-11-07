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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Epic("API")
@Feature("Concurrency")
public class ConcurrencyApiTests {

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

    @Test(description = "Ten simultaneous picks of the last five units: exactly five succeed, stock never goes negative")
    public void parallelPicksNeverOversell() throws Exception {
        String sku = ApiClient.createItem(admin, "Race Pick", 5);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                results.add(pool.submit(() -> ApiClient.authed(admin)
                        .body(json("quantity", 1))
                        .when().post("/api/items/" + sku + "/pick")
                        .then().extract().statusCode()));
            }
            int ok = 0;
            int conflict = 0;
            for (Future<Integer> f : results) {
                int status = f.get();
                if (status == 200) {
                    ok++;
                } else if (status == 409) {
                    conflict++;
                }
            }
            Assert.assertEquals(ok, 5, "exactly five picks should succeed");
            Assert.assertEquals(conflict, 5, "the other five should be refused");
            Assert.assertEquals(ApiClient.quantityOf(admin, sku), 0);
        } finally {
            pool.shutdown();
        }
    }

    @Test(description = "Five simultaneous orders for the last four units: exactly four are accepted")
    public void parallelSalesOrdersNeverOverCommit() throws Exception {
        String sku = ApiClient.createItem(admin, "Race Sell", 4);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                results.add(pool.submit(() -> ApiClient.authed(admin)
                        .body(json("customer", "Shop", "sku", sku, "quantity", 1))
                        .when().post("/api/sales-orders")
                        .then().extract().statusCode()));
            }
            int created = 0;
            for (Future<Integer> f : results) {
                if (f.get() == 201) {
                    created++;
                }
            }
            Assert.assertEquals(created, 4, "only four units can be promised");
        } finally {
            pool.shutdown();
        }
    }
}
