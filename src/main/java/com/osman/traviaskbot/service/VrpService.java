package com.osman.traviaskbot.service;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import com.google.ortools.util.OptionalBoolean;
import com.osman.traviaskbot.controller.RouteController.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

@Service
@Slf4j
public class VrpService {

    /* ————————————————— public API ————————————————— */

    /**
     * @param driverStarts   hub koordinatları
     * @param pickups        müşteri koordinatları
     * @param paxList        her pickup’ın yolcu sayısı
     * @param regions        Region.KEMER / SIDE / OTHER kodları
     * @param isKemerDriver  her aracın Kemer çıkışlı olup olmadığı
     * @param numVehicles    toplam araç
     */
    public Map<Integer, List<Integer>> solveVrp(
            List<double[]> driverStarts,
            List<double[]> pickups,
            List<Integer>  paxList,
            List<Integer>  regions,
            List<Boolean>  isKemerDriver,
            int            numVehicles) {

        Loader.loadNativeLibraries();

        /* ---------- temel metrikler ---------- */
        final int H = driverStarts.size();
        final int N = pickups.size();
        final int nodeCount = H + N + 1;             // + depo

        RoutingIndexManager mgr = new RoutingIndexManager(
                nodeCount,
                numVehicles,
                IntStream.range(0, H).toArray(),      // starts
                IntStream.generate(() -> nodeCount - 1)
                        .limit(numVehicles).toArray()/* ends (=depo) */
        );
        RoutingModel routing = new RoutingModel(mgr);

        /* ---------- mesafe matrisi ---------- */
        long[][] dist = buildDistanceMatrix(driverStarts, pickups, nodeCount, H);

        int transitCb = routing.registerTransitCallback((f, t) -> {
            int i = mgr.indexToNode(f);
            int j = mgr.indexToNode(t);
            long d = dist[i][j];
            return routing.isStart(f) ? d * 5 /* ilk bacak cezası */ : d;
        });
        routing.setArcCostEvaluatorOfAllVehicles(transitCb);

        /* ---------- yolcu kapasitesi ---------- */
        long[] demand = new long[nodeCount];
        for (int i = 0; i < N; i++) demand[H + i] = paxList.get(i);

        int demandCb = routing.registerUnaryTransitCallback(idx ->
                demand[mgr.indexToNode(idx)]);

        routing.addDimensionWithVehicleCapacity(
                demandCb, 0,
                LongStream.generate(() -> 16L).limit(numVehicles).toArray(),
                true, "Capacity"
        );

        /* ---------- bölge kısıtları ---------- */
        int[] kemerVeh = IntStream.range(0, numVehicles)
                .filter(isKemerDriver::get)
                .toArray();
        int[] nonKemer = IntStream.range(0, numVehicles)
                .filter(v -> !isKemerDriver.get(v))
                .toArray();

        for (int k = 0; k < N; k++) {
            long nodeIdx = mgr.nodeToIndex(H + k);
            switch (Region.values()[regions.get(k)]) {
                case SIDE  -> routing.setAllowedVehiclesForIndex(nonKemer, nodeIdx);
                case KEMER -> routing.setAllowedVehiclesForIndex(kemerVeh,  nodeIdx);
                default    -> { /* OTHER – serbest */ }
            }
        }

        /* ---------- mesafe dimension ---------- */
        routing.addDimension(transitCb, 0, 1_000_000_000L, true, "Distance");
        routing.getDimensionOrDie("Distance").setGlobalSpanCostCoefficient(500);

        /* ---------- optional pickup + unused cezası ---------- */
        long pickupPenalty = 10_000_000L;
        for (int i = 0; i < N; i++)
            routing.addDisjunction(new long[]{mgr.nodeToIndex(H + i)}, pickupPenalty);

        long unusedPenalty = 1_000_000L;
        for (int v = 0; v < numVehicles; v++)
            routing.addDisjunction(new long[]{routing.start(v)}, unusedPenalty);

        /* ---------- arama parametreleri ---------- */
        RoutingSearchParameters params = main.defaultRoutingSearchParameters().toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.SAVINGS)
                .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                .setTimeLimit(Duration.newBuilder().setSeconds(5).build())
                .setLocalSearchOperators(
                        main.defaultRoutingSearchParameters().getLocalSearchOperators().toBuilder()
                                .setUseRelocate(OptionalBoolean.BOOL_TRUE)
                                .setUseTwoOpt(OptionalBoolean.BOOL_TRUE)
                                .setUseExchange(OptionalBoolean.BOOL_TRUE)
                                .build()
                ).build();

        Assignment sol = routing.solveWithParameters(params);
        if (sol == null) {
            log.warn("❌ VRP çözüm bulunamadı");
            return Collections.emptyMap();
        }

        /* ---------- rotaları çıkar ---------- */
        Map<Integer, List<Integer>> routes = new LinkedHashMap<>();
        for (int v = 0; v < numVehicles; v++) {
            List<Integer> route = new ArrayList<>();
            for (long idx = routing.start(v); !routing.isEnd(idx);
                 idx = sol.value(routing.nextVar(idx)))
                route.add(mgr.indexToNode(idx));

            route.add(mgr.indexToNode(routing.end(v))); // depo
            routes.put(v, route);
        }
        return routes;
    }

    /* ————————————————— helpers ————————————————— */

    private long[][] buildDistanceMatrix(List<double[]> hubs,
                                         List<double[]> pick,
                                         int nodeCount, int H) {

        double[][] P = new double[nodeCount][2];
        for (int i = 0; i < H; i++)          P[i]      = hubs.get(i);
        for (int i = 0; i < pick.size(); i++)P[H + i]  = pick.get(i);
        P[nodeCount - 1] = new double[]{36.876074, 31.086317}; // depo

        long[][] d = new long[nodeCount][nodeCount];
        for (int i = 0; i < nodeCount; i++)
            for (int j = 0; j < nodeCount; j++)
                d[i][j] = (i == j) ? 0 : Math.round(haversine(P[i], P[j]) * 1000);

        return d;
    }

    /** haversine – km */
    private static double haversine(double[] a, double[] b) {
        double R = 6371,
                dLat = Math.toRadians(b[0] - a[0]),
                dLng = Math.toRadians(b[1] - a[1]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(a[0])) *
                        Math.cos(Math.toRadians(b[0])) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.sqrt(h));
    }
}
