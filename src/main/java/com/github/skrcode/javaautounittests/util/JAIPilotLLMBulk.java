//package com.github.skrcode.javaautounittests.util;
//
//import com.github.skrcode.javaautounittests.DTOs.BulkGenerationRequest;
//import com.github.skrcode.javaautounittests.DTOs.PromptResponseOutput;
//import com.github.skrcode.javaautounittests.JAIPilotLLM;
//import com.intellij.openapi.progress.ProcessCanceledException;
//import com.intellij.openapi.progress.ProgressIndicator;
//import com.intellij.util.concurrency.AppExecutorUtil;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.*;
//
//public final class JAIPilotLLMBulk {
//
//    public static List<PromptResponseOutput> generateContentBulk(
//            List<BulkGenerationRequest> requests,
//            ProgressIndicator indicator,
//            int maxParallelCalls
//    ) {
//
//        if (requests == null || requests.isEmpty()) {
//            return List.of();
//        }
//
//        // Cap parallelism: useful if you call LLM via HTTP and want to avoid throttling / rate limits
//        int parallelism = Math.max(1, Math.min(maxParallelCalls, requests.size()));
//
//        ExecutorService executor = new ThreadPoolExecutor(
//                parallelism,
//                parallelism,
//                60L,
//                TimeUnit.SECONDS,
//                new LinkedBlockingQueue<>(),
//                AppExecutorUtil.createBoundedApplicationPoolExecutor("JAIPilot-LLM-Bulk", parallelism)
//        );
//
//        try {
//            List<CompletableFuture<PromptResponseOutput>> futures = new ArrayList<>(requests.size());
//
//            for (BulkGenerationRequest req : requests) {
//                indicator.checkCanceled();
//
//                CompletableFuture<PromptResponseOutput> future =
//                        CompletableFuture.supplyAsync(() -> {
//                            // Each task must also respect cancellation
//                            indicator.checkCanceled();
//                            return JAIPilotLLM.generateContent(
//                                    req.testFileName,
//                                    req.contents,
//                                    req.console,
//                                    req.attempt,
//                                    indicator
//                            );
//                        }, executor);
//
//                futures.add(future);
//            }
//
//            // “async/await all” – wait for all futures, or cancel if indicator is canceled
//            List<PromptResponseOutput> results = new ArrayList<>(requests.size());
//            for (CompletableFuture<PromptResponseOutput> f : futures) {
//                indicator.checkCanceled();
//                try {
//                    // blocking wait for each result; we’ve already started them all
//                    results.add(f.get());
//                } catch (InterruptedException e) {
//                    // Preserve interrupt status and cancel everything
//                    Thread.currentThread().interrupt();
//                    cancelAll(futures);
//                    throw new ProcessCanceledException(e);
//                } catch (ExecutionException e) {
//                    // If one task fails, you can either:
//                    // 1) fail fast and cancel all others, or
//                    // 2) log and continue.
//                    // Here we choose fail-fast + cancel.
//                    cancelAll(futures);
//                    // Rethrow the *root cause*; JAIPilot caller can handle it.
//                    if (e.getCause() instanceof RuntimeException re) {
//                        throw re;
//                    }
//                    throw new RuntimeException(e.getCause());
//                }
//            }
//
//            return results;
//        } finally {
//            executor.shutdownNow();
//        }
//    }
//
//    private static void cancelAll(List<CompletableFuture<PromptResponseOutput>> futures) {
//        for (CompletableFuture<PromptResponseOutput> f : futures) {
//            f.cancel(true);
//        }
//    }
//
//    // Convenience overload with a sane default parallelism
//    public static List<PromptResponseOutput> generateContentBulk(
//            List<BulkGenerationRequest> requests,
//            ProgressIndicator indicator
//    ) {
//        int defaultParallelism = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
//        return generateContentBulk(requests, indicator, defaultParallelism);
//    }
//
//    private JAIPilotLLMBulk() {}
//}
