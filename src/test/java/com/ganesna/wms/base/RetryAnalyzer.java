package com.ganesna.wms.base;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/** Retries a failed test up to -Dretry.count times (default 1). Use -Dretry.count=0 to turn retries off. */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final int MAX_RETRIES = Integer.getInteger("retry.count", 1);
    private int attempt = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (attempt < MAX_RETRIES) {
            attempt++;
            System.out.println("[retry] " + result.getName() + " failed, retry "
                    + attempt + "/" + MAX_RETRIES);
            return true;
        }
        return false;
    }
}