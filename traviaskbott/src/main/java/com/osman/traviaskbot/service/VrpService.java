// src/main/java/com/osman/traviaskbot/service/VrpService.java
package com.osman.traviaskbot.service;

import com.google.protobuf.Descriptors.Descriptor;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.FirstSolutionStrategy;
import com.google.ortools.constraintsolver.LocalSearchMetaheuristic;
import com.google.ortools.constraintsolver.main;
import com.google.ortools.constraintsolver.RoutingDimension;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.google.ortools.constraintsolver.RoutingSearchParameters;
import com.google.ortools.constraintsolver.RoutingSearchParameters.LocalSearchNeighborhoodOperators;
                     // ← defaultRoutingSearchParameters()
import com.google.ortools.util.OptionalBoolean;                     // ← proto enu
import com.google.ortools.constraintsolver.RoutingSearchParameters.LocalSearchNeighborhoodOperators;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Duration;
import com.google.ortools.util.OptionalBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

@Service
@Slf4j
public class VrpService {

    public Map<Integer, List<Integer>> solveVrp(
            List<double[]> driverStarts,
            List<double[]> pickups,
            List<Integer> paxList,
            int numVehicleParam) {

        Loader.loadNativeLibraries();

        // 1) Araç ve düğüm sayıları
        int D = driverStarts.size();
        int N = pickups.size();
        int vehicleCount = D;
        int nodeCount = D + N + 1;

        // 2) Başlangıç/bitiş indeksleri
        int[] starts = IntStream.range(0, D).toArray();
        int[] ends   = IntStream.generate(() -> nodeCount - 1)
                .limit(vehicleCount)
                .toArray();

        // 3) Manager & Model
        RoutingIndexManager mgr     = new RoutingIndexManager(nodeCount, vehicleCount, starts, ends);
        RoutingModel        routing = new RoutingModel(mgr);

        // 4) Koordinatlar ve mesafe matrisi
        double[][] nodes = new double[nodeCount][2];
        for (int i = 0; i < D; i++)      nodes[i]     = driverStarts.get(i);
        for (int i = 0; i < N; i++)      nodes[D + i] = pickups.get(i);
        nodes[nodeCount - 1] = new double[]{36.876074, 31.086317};  // depo

        long[][] dist = new long[nodeCount][nodeCount];
        for (int i = 0; i < nodeCount; i++)
            for (int j = 0; j < nodeCount; j++)
                dist[i][j] = (i == j)
                        ? 0
                        : Math.round(haversine(nodes[i], nodes[j]) * 1000);

        // 5) Transit callback & cost
        int transitCb = routing.registerTransitCallback((long fromIdx, long toIdx) -> {
            int i = mgr.indexToNode(fromIdx);
            int j = mgr.indexToNode(toIdx);
            return dist[i][j];
        });
        routing.setArcCostEvaluatorOfAllVehicles(transitCb);

        // 6) Kapasite dimension
        long[] demand = new long[nodeCount];
        for (int i = 0; i < N; i++) demand[D + i] = paxList.get(i);
        int demandCb = routing.registerUnaryTransitCallback(idx ->
                demand[mgr.indexToNode(idx)]
        );
        long[] caps = LongStream.generate(() -> 16L).limit(vehicleCount).toArray();
        routing.addDimensionWithVehicleCapacity(
                demandCb, 0, caps, true, "Capacity"
        );

        // 7) Distance dimension (global span cost)
        routing.addDimension(transitCb, 0, 1_000_000_000L, true, "Distance");
        RoutingDimension distDim = routing.getDimensionOrDie("Distance");
        distDim.setGlobalSpanCostCoefficient(500);

        // 8) Pickup’ları optional yap
        long pickupPenalty = 10_000000L;
        for (int i = 0; i < N; i++) {
            long idx = mgr.nodeToIndex(D + i);
            routing.addDisjunction(new long[]{idx}, pickupPenalty);
        }

        // 9) Araç unused cezası
        long vehicleUnusedPenalty = 1000000L;
        for (int v = 0; v < vehicleCount; v++) {
            long startIdx = routing.start(v);
            routing.addDisjunction(new long[]{startIdx}, vehicleUnusedPenalty);
        }

        // … önceki kod …

// 10) Search parameters oluştur (varsayılanı alıp sadece 3 operatörü değiştiriyoruz)
        // --------------------------------------------------------------------------------
        // 10a) Varsayılan parametreleri al
        RoutingSearchParameters defaultParams =
                main.defaultRoutingSearchParameters();

        // 10b) İçindeki operatör setini al
        LocalSearchNeighborhoodOperators defaultOps =
                defaultParams.getLocalSearchOperators();

        // 10c) Sadece ihtiyacımız olan üç operatörü açık bırak, geri kalanı otomatik olarak kalır
        LocalSearchNeighborhoodOperators customOps = defaultOps.toBuilder()
                .setUseRelocate(OptionalBoolean.BOOL_TRUE)
                .setUseTwoOpt(OptionalBoolean.BOOL_TRUE)
                .setUseExchange(OptionalBoolean.BOOL_TRUE)
                .build();

        // 10d) Parametreleri güncelle ve customOps’u yerleştir
        RoutingSearchParameters params = defaultParams.toBuilder()
                .setFirstSolutionStrategy(
                        FirstSolutionStrategy.Value.SAVINGS)
                .setLocalSearchMetaheuristic(
                        LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                .setTimeLimit(Duration.newBuilder().setSeconds(5).build())
                .setLocalSearchOperators(customOps)
                .build();

        // 11) Çözümü çalıştır
        Assignment sol = routing.solveWithParameters(params);

// … sonraki kod …




        if (sol == null) {
            log.warn("❌ VRP çözüm bulunamadı");
            return Collections.emptyMap();
        }

        // 12) Rotaları oku
        Map<Integer, List<Integer>> result = new LinkedHashMap<>();
        for (int v = 0; v < vehicleCount; v++) {
            long idx = routing.start(v);
            List<Integer> route = new ArrayList<>();
            while (!routing.isEnd(idx)) {
                route.add(mgr.indexToNode(idx));
                idx = sol.value(routing.nextVar(idx));
            }
            route.add(mgr.indexToNode(idx));  // depo
            result.put(v, route);
        }
        return result;
    }

    /** Haversine (km) */
    private static double haversine(double[] a, double[] b) {
        double R    = 6371;
        double dLat = Math.toRadians(b[0] - a[0]);
        double dLng = Math.toRadians(b[1] - a[1]);
        double s = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(a[0]))*Math.cos(Math.toRadians(b[0]))
                * Math.sin(dLng/2)*Math.sin(dLng/2);
        return 2 * R * Math.asin(Math.sqrt(s));
    }
}
