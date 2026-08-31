package com.wzh.blog.service.impl;

import com.wzh.blog.service.RedisService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metric;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Small process-local implementation used when Redis is disabled.
 *
 * <p>Redis is a cache, deduplication and coordination capability in this
 * application; it must not be a prerequisite for the MySQL-backed core.
 * This implementation deliberately mirrors only the existing compatibility
 * interface. It is bounded by expiry where callers provide it, has hard caps
 * for keys and collection-like values, and is never used as a durable source
 * of truth.</p>
 */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRedisService implements RedisService {

    static final int MAX_KEY_COUNT = 2048;
    static final int MAX_COLLECTION_ENTRIES = 2048;
    static final int MAX_BITMAP_BITS = 8 * 1024 * 1024;

    private final ConcurrentMap<String, Object> values = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Object>> hashes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<Object>> sets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<Object>> lists = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, NavigableMap<Object, Double>> sortedSets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BitSet> bitmaps = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<Object>> hyperLogs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Point>> geos = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> expiresAt = new ConcurrentHashMap<>();
    private final LinkedHashMap<String, Boolean> keyOrder = new LinkedHashMap<>(32, 0.75f, true);

    @Override
    public synchronized void set(String key, Object value, long time) {
        requireKey(key);
        clearOtherTypes(key, values);
        values.put(key, value);
        remember(key);
        setExpiry(key, time);
    }

    @Override
    public synchronized void set(String key, Object value) {
        requireKey(key);
        clearOtherTypes(key, values);
        values.put(key, value);
        remember(key);
        expiresAt.remove(key);
    }

    @Override
    public synchronized Object get(String key) {
        purge(key);
        return values.get(key);
    }

    @Override
    public synchronized Boolean del(String key) {
        return removeKey(key);
    }

    @Override
    public synchronized Boolean consumeIfEquals(String key, Object value) {
        purge(key);
        if (Objects.equals(values.get(key), value)) {
            removeKey(key);
            return true;
        }
        return false;
    }

    @Override
    public synchronized Boolean toggleMemberAndCount(String memberSetKey, Object member, String countHashKey) {
        purge(memberSetKey);
        purge(countHashKey);
        Set<Object> members = sets.computeIfAbsent(memberSetKey, ignored -> new LinkedHashSet<>());
        String hashMember = String.valueOf(member);
        Map<String, Object> counts = hashes.computeIfAbsent(countHashKey, ignored -> new LinkedHashMap<>());
        long count = number(counts.get(hashMember));
        if (members.remove(member)) {
            counts.put(hashMember, Math.max(0L, count - 1));
            trimSet(members);
            trimMap(counts);
            remember(memberSetKey);
            remember(countHashKey);
            return false;
        }
        members.add(member);
        counts.put(hashMember, count + 1);
        trimSet(members);
        trimMap(counts);
        remember(memberSetKey);
        remember(countHashKey);
        return true;
    }

    @Override
    public synchronized Boolean recordUniqueVisitor(String visitorSetKey, Object visitor, String viewsKey,
                                                    String areaHashKey, String area) {
        purge(visitorSetKey);
        Set<Object> visitors = sets.computeIfAbsent(visitorSetKey, ignored -> new LinkedHashSet<>());
        if (!visitors.add(visitor)) {
            return false;
        }
        incrementValue(viewsKey, 1);
        Map<String, Object> areas = hashes.computeIfAbsent(areaHashKey, ignored -> new LinkedHashMap<>());
        String areaKey = area == null ? "" : area;
        areas.put(areaKey, number(areas.get(areaKey)) + 1);
        trimSet(visitors);
        trimMap(areas);
        remember(visitorSetKey);
        remember(viewsKey);
        remember(areaHashKey);
        return true;
    }

    @Override
    public synchronized Long del(List<String> keys) {
        long deleted = 0;
        if (keys != null) {
            for (String key : keys) {
                if (Boolean.TRUE.equals(removeKey(key))) {
                    deleted++;
                }
            }
        }
        return deleted;
    }

    @Override
    public synchronized Boolean expire(String key, long time) {
        purge(key);
        if (!containsKey(key)) {
            return false;
        }
        if (time <= 0) {
            removeKey(key);
        } else {
            expiresAt.put(key, System.currentTimeMillis() + Duration.ofSeconds(time).toMillis());
        }
        return true;
    }

    @Override
    public synchronized Long getExpire(String key) {
        purge(key);
        if (!containsKey(key)) {
            return -2L;
        }
        Long expires = expiresAt.get(key);
        if (expires == null) {
            return -1L;
        }
        return Math.max(0L, (expires - System.currentTimeMillis() + 999L) / 1000L);
    }

    @Override
    public synchronized Boolean hasKey(String key) {
        purge(key);
        return containsKey(key);
    }

    @Override
    public synchronized Long incr(String key, long delta) {
        return incrementValue(key, delta);
    }

    @Override
    public synchronized Long incrExpire(String key, long time) {
        long value = incrementValue(key, 1);
        if (value == 1) {
            setExpiry(key, time);
        }
        return value;
    }

    @Override
    public synchronized Long decr(String key, long delta) {
        return incrementValue(key, -delta);
    }

    @Override
    public synchronized Object hGet(String key, String hashKey) {
        purge(key);
        Map<String, Object> hash = hashes.get(key);
        return hash == null ? null : hash.get(hashKey);
    }

    @Override
    public synchronized Boolean hSet(String key, String hashKey, Object value, long time) {
        hSet(key, hashKey, value);
        return expire(key, time);
    }

    @Override
    public synchronized void hSet(String key, String hashKey, Object value) {
        purge(key);
        Map<String, Object> hash = hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        hash.put(hashKey, value);
        trimMap(hash);
        remember(key);
    }

    @Override
    public synchronized Map<String, Object> hGetAll(String key) {
        purge(key);
        Map<String, Object> hash = hashes.get(key);
        return hash == null ? new LinkedHashMap<>() : new LinkedHashMap<>(hash);
    }

    @Override
    public synchronized Boolean hSetAll(String key, Map<String, Object> map, long time) {
        hSetAll(key, map);
        return expire(key, time);
    }

    @Override
    public synchronized void hSetAll(String key, Map<String, ?> map) {
        purge(key);
        Map<String, Object> hash = hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        if (map != null) {
            map.forEach(hash::put);
        }
        trimMap(hash);
        remember(key);
    }

    @Override
    public synchronized void hDel(String key, Object... hashKey) {
        purge(key);
        Map<String, Object> hash = hashes.get(key);
        if (hash != null && hashKey != null) {
            for (Object field : hashKey) {
                hash.remove(String.valueOf(field));
            }
            if (hash.isEmpty()) {
                removeKey(key);
            }
        }
    }

    @Override
    public synchronized Boolean hHasKey(String key, String hashKey) {
        purge(key);
        return hashes.containsKey(key) && hashes.get(key).containsKey(hashKey);
    }

    @Override
    public synchronized Long hIncr(String key, String hashKey, Long delta) {
        purge(key);
        Map<String, Object> hash = hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        long result = number(hash.get(hashKey)) + (delta == null ? 0L : delta);
        hash.put(hashKey, result);
        trimMap(hash);
        remember(key);
        return result;
    }

    @Override
    public synchronized Long hDecr(String key, String hashKey, Long delta) {
        return hIncr(key, hashKey, delta == null ? 0L : -delta);
    }

    @Override
    public synchronized Double zIncr(String key, Object value, Double score) {
        return incrementScore(key, value, score == null ? 0d : score);
    }

    @Override
    public synchronized Double zDecr(String key, Object value, Double score) {
        return incrementScore(key, value, score == null ? 0d : -score);
    }

    @Override
    public synchronized Map<Object, Double> zReverseRangeWithScore(String key, long start, long end) {
        purge(key);
        NavigableMap<Object, Double> set = sortedSets.get(key);
        if (set == null) {
            return new LinkedHashMap<>();
        }
        List<Map.Entry<Object, Double>> entries = set.entrySet().stream()
                .sorted(Map.Entry.<Object, Double>comparingByValue().reversed()
                        .thenComparing(entry -> String.valueOf(entry.getKey())))
                .toList();
        int from = Math.max(0, (int) start);
        int to = end < 0 ? entries.size() : Math.min(entries.size(), (int) end + 1);
        if (from >= to || from >= entries.size()) {
            return new LinkedHashMap<>();
        }
        return entries.subList(from, to).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
    }

    @Override
    public synchronized Double zScore(String key, Object value) {
        purge(key);
        NavigableMap<Object, Double> set = sortedSets.get(key);
        return set == null ? null : set.get(value);
    }

    @Override
    public synchronized Map<Object, Double> zAllScore(String key) {
        purge(key);
        NavigableMap<Object, Double> set = sortedSets.get(key);
        return set == null ? new LinkedHashMap<>() : new LinkedHashMap<>(set);
    }

    @Override
    public synchronized Set<Object> sMembers(String key) {
        purge(key);
        Set<Object> set = sets.get(key);
        return set == null ? new LinkedHashSet<>() : new LinkedHashSet<>(set);
    }

    @Override
    public synchronized Long sAdd(String key, Object... valuesToAdd) {
        purge(key);
        Set<Object> set = sets.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
        long added = 0;
        if (valuesToAdd != null) {
            for (Object value : valuesToAdd) {
                if (set.add(value)) {
                    added++;
                }
            }
        }
        trimSet(set);
        remember(key);
        return added;
    }

    @Override
    public synchronized Long sAddExpire(String key, long time, Object... valuesToAdd) {
        long added = sAdd(key, valuesToAdd);
        expire(key, time);
        return added;
    }

    @Override
    public synchronized Boolean sIsMember(String key, Object value) {
        purge(key);
        return sets.containsKey(key) && sets.get(key).contains(value);
    }

    @Override
    public synchronized Long sSize(String key) {
        purge(key);
        return sets.containsKey(key) ? (long) sets.get(key).size() : 0L;
    }

    @Override
    public synchronized Long sRemove(String key, Object... valuesToRemove) {
        purge(key);
        Set<Object> set = sets.get(key);
        if (set == null || valuesToRemove == null) {
            return 0L;
        }
        long removed = 0;
        for (Object value : valuesToRemove) {
            if (set.remove(value)) {
                removed++;
            }
        }
        if (set.isEmpty()) {
            removeKey(key);
        }
        return removed;
    }

    @Override
    public synchronized List<Object> lRange(String key, long start, long end) {
        purge(key);
        List<Object> list = lists.get(key);
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        int from = normalizeIndex(start, list.size());
        int to = end < 0 ? list.size() - 1 : normalizeIndex(end, list.size());
        if (from > to || from >= list.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list.subList(from, Math.min(list.size(), to + 1)));
    }

    @Override
    public synchronized Long lSize(String key) {
        purge(key);
        return lists.containsKey(key) ? (long) lists.get(key).size() : 0L;
    }

    @Override
    public synchronized Object lIndex(String key, long index) {
        purge(key);
        List<Object> list = lists.get(key);
        if (list == null) {
            return null;
        }
        int position = normalizeIndex(index, list.size());
        return position >= 0 && position < list.size() ? list.get(position) : null;
    }

    @Override
    public synchronized Long lPush(String key, Object value) {
        return append(key, List.of(value));
    }

    @Override
    public synchronized Long lPush(String key, Object value, long time) {
        long size = append(key, List.of(value));
        expire(key, time);
        return size;
    }

    @Override
    public synchronized Long lPushAll(String key, Object... valuesToAdd) {
        return append(key, valuesToAdd == null ? List.of() : List.of(valuesToAdd));
    }

    @Override
    public synchronized Long lPushAll(String key, Long time, Object... valuesToAdd) {
        long size = lPushAll(key, valuesToAdd);
        expire(key, time == null ? 0L : time);
        return size;
    }

    @Override
    public synchronized Long lRemove(String key, long count, Object value) {
        purge(key);
        List<Object> list = lists.get(key);
        if (list == null || count == 0) {
            return 0L;
        }
        long removed = 0;
        if (count > 0) {
            for (int i = 0; i < list.size() && removed < count; ) {
                if (Objects.equals(list.get(i), value)) {
                    list.remove(i);
                    removed++;
                } else {
                    i++;
                }
            }
        } else {
            for (int i = list.size() - 1; i >= 0; i--) {
                if (Objects.equals(list.get(i), value)) {
                    list.remove(i);
                    removed++;
                }
            }
        }
        if (list.isEmpty()) {
            removeKey(key);
        }
        return removed;
    }

    @Override
    public synchronized Boolean bitAdd(String key, int offset, boolean value) {
        requireBitmapOffset(offset);
        purge(key);
        BitSet bitmap = bitmaps.computeIfAbsent(key, ignored -> new BitSet());
        boolean previous = bitmap.get(offset);
        bitmap.set(offset, value);
        remember(key);
        return previous;
    }

    @Override
    public synchronized Boolean bitGet(String key, int offset) {
        if (offset < 0 || offset > MAX_BITMAP_BITS) {
            return false;
        }
        purge(key);
        return bitmaps.containsKey(key) && bitmaps.get(key).get(offset);
    }

    @Override
    public synchronized Long bitCount(String key) {
        purge(key);
        return bitmaps.containsKey(key) ? (long) bitmaps.get(key).cardinality() : 0L;
    }

    @Override
    public synchronized List<Long> bitField(String key, int limit, int offset) {
        if (limit <= 0) {
            return List.of(0L);
        }
        long value = 0L;
        for (int index = 0; index < limit && index < Long.SIZE; index++) {
            if (Boolean.TRUE.equals(bitGet(key, offset + index))) {
                value |= 1L << (limit - index - 1);
            }
        }
        return List.of(value);
    }

    @Override
    public synchronized byte[] bitGetAll(String key) {
        purge(key);
        BitSet bitmap = bitmaps.get(key);
        if (bitmap == null || bitmap.length() == 0) {
            return new byte[0];
        }
        byte[] bytes = new byte[(bitmap.length() + 7) / 8];
        for (int bit = bitmap.nextSetBit(0); bit >= 0; bit = bitmap.nextSetBit(bit + 1)) {
            bytes[bit / 8] |= (byte) (1 << (7 - bit % 8));
        }
        return bytes;
    }

    @Override
    public synchronized Long hyperAdd(String key, Object... value) {
        purge(key);
        Set<Object> set = hyperLogs.computeIfAbsent(key, ignored -> new HashSet<>());
        long sizeBefore = set.size();
        if (value != null) {
            Collections.addAll(set, value);
        }
        trimSet(set);
        remember(key);
        return (long) set.size() - sizeBefore;
    }

    @Override
    public synchronized Long hyperGet(String... keys) {
        long total = 0;
        if (keys != null) {
            for (String key : keys) {
                purge(key);
                Set<Object> set = hyperLogs.get(key);
                if (set != null) {
                    total += set.size();
                }
            }
        }
        return total;
    }

    @Override
    public synchronized void hyperDel(String key) {
        removeKey(key);
    }

    @Override
    public synchronized Long geoAdd(String key, Double x, Double y, String name) {
        purge(key);
        Map<String, Point> places = geos.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        boolean newPlace = !places.containsKey(name);
        places.put(name, new Point(x, y));
        trimMap(places);
        remember(key);
        return newPlace ? 1L : 0L;
    }

    @Override
    public synchronized List<Point> geoGetPointList(String key, Object... place) {
        purge(key);
        Map<String, Point> places = geos.get(key);
        if (places == null || place == null) {
            return new ArrayList<>();
        }
        return List.of(place).stream()
                .map(value -> places.get(String.valueOf(value)))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public synchronized Distance geoCalculationDistance(String key, String placeOne, String placeTow) {
        purge(key);
        Map<String, Point> places = geos.get(key);
        if (places == null || places.get(placeOne) == null || places.get(placeTow) == null) {
            return null;
        }
        return new Distance(distanceInKilometers(places.get(placeOne), places.get(placeTow)), Metrics.KILOMETERS);
    }

    @Override
    public synchronized GeoResults<RedisGeoCommands.GeoLocation<Object>> geoNearByPlace(String key, String place,
                                                                                          Distance distance, long limit,
                                                                                          Sort.Direction sort) {
        purge(key);
        Map<String, Point> places = geos.get(key);
        Point origin = places == null ? null : places.get(place);
        if (origin == null || distance == null) {
            return new GeoResults<>(List.of());
        }
        double maxKilometers = distance.in(Metrics.KILOMETERS).getValue();
        Comparator<GeoResult<RedisGeoCommands.GeoLocation<Object>>> comparator =
                Comparator.comparing(result -> result.getDistance().in(Metrics.KILOMETERS).getValue());
        if (sort == Sort.Direction.DESC) {
            comparator = comparator.reversed();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<Object>>> result = places.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), distanceInKilometers(origin, entry.getValue())))
                .filter(entry -> entry.getValue() <= maxKilometers)
                .map(entry -> new GeoResult<RedisGeoCommands.GeoLocation<Object>>(
                        new RedisGeoCommands.GeoLocation<>(entry.getKey(), places.get(entry.getKey())),
                        new Distance(entry.getValue(), Metrics.KILOMETERS)))
                .sorted(comparator)
                .limit(Math.max(0L, limit))
                .toList();
        return new GeoResults<>(result, Metrics.KILOMETERS);
    }

    @Override
    public synchronized List<String> geoGetHash(String key, String... place) {
        purge(key);
        Map<String, Point> places = geos.get(key);
        if (places == null || place == null) {
            return new ArrayList<>();
        }
        return List.of(place).stream()
                .filter(value -> places.containsKey(value))
                .map(value -> Integer.toHexString(value.hashCode()))
                .toList();
    }

    private long incrementValue(String key, long delta) {
        purge(key);
        long result = number(values.get(key)) + delta;
        clearOtherTypes(key, values);
        values.put(key, result);
        remember(key);
        return result;
    }

    private Double incrementScore(String key, Object member, double delta) {
        purge(key);
        NavigableMap<Object, Double> scores = sortedSets.computeIfAbsent(key,
                ignored -> new TreeMap<>(Comparator.comparing(String::valueOf)));
        double result = scores.getOrDefault(member, 0d) + delta;
        scores.put(member, result);
        trimMap(scores);
        remember(key);
        return result;
    }

    private long append(String key, List<?> valuesToAdd) {
        purge(key);
        List<Object> list = lists.computeIfAbsent(key, ignored -> new ArrayList<>());
        list.addAll(valuesToAdd);
        trimList(list);
        remember(key);
        return list.size();
    }

    private void setExpiry(String key, long seconds) {
        if (seconds <= 0) {
            removeKey(key);
        } else {
            expiresAt.put(key, System.currentTimeMillis() + Duration.ofSeconds(seconds).toMillis());
        }
    }

    private void purge(String key) {
        Long expires = expiresAt.get(key);
        if (expires != null && expires <= System.currentTimeMillis()) {
            removeKey(key);
        }
        if (containsKey(key)) {
            remember(key);
        } else {
            evictIfNecessary();
        }
    }

    private boolean containsKey(String key) {
        return values.containsKey(key) || hashes.containsKey(key) || sets.containsKey(key)
                || lists.containsKey(key) || sortedSets.containsKey(key) || bitmaps.containsKey(key)
                || hyperLogs.containsKey(key) || geos.containsKey(key);
    }

    private Boolean removeKey(String key) {
        boolean existed = containsKey(key);
        values.remove(key);
        hashes.remove(key);
        sets.remove(key);
        lists.remove(key);
        sortedSets.remove(key);
        bitmaps.remove(key);
        hyperLogs.remove(key);
        geos.remove(key);
        expiresAt.remove(key);
        keyOrder.remove(key);
        return existed;
    }

    private void clearOtherTypes(String key, ConcurrentMap<String, ?> keep) {
        if (keep != values) {
            values.remove(key);
        }
        if (keep != hashes) {
            hashes.remove(key);
        }
        if (keep != sets) {
            sets.remove(key);
        }
        if (keep != lists) {
            lists.remove(key);
        }
        if (keep != sortedSets) {
            sortedSets.remove(key);
        }
        if (keep != bitmaps) {
            bitmaps.remove(key);
        }
        if (keep != hyperLogs) {
            hyperLogs.remove(key);
        }
        if (keep != geos) {
            geos.remove(key);
        }
    }

    private void remember(String key) {
        if (key == null || !containsKey(key)) {
            return;
        }
        keyOrder.put(key, Boolean.TRUE);
        evictIfNecessary();
    }

    private void evictIfNecessary() {
        while (keyOrder.size() > MAX_KEY_COUNT) {
            Iterator<String> iterator = keyOrder.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            String oldest = iterator.next();
            removeKey(oldest);
        }
    }

    private <K, V> void trimMap(Map<K, V> map) {
        while (map.size() > MAX_COLLECTION_ENTRIES) {
            Iterator<K> iterator = map.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            map.remove(iterator.next());
        }
    }

    private <T> void trimSet(Set<T> set) {
        while (set.size() > MAX_COLLECTION_ENTRIES) {
            Iterator<T> iterator = set.iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private void trimList(List<Object> list) {
        int overflow = list.size() - MAX_COLLECTION_ENTRIES;
        if (overflow > 0) {
            list.subList(0, overflow).clear();
        }
    }

    private void requireBitmapOffset(int offset) {
        if (offset < 0 || offset > MAX_BITMAP_BITS) {
            throw new IllegalArgumentException("bitmap offset exceeds local fallback limit");
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static int normalizeIndex(long index, int size) {
        if (index < 0) {
            return Math.max(0, size + (int) index);
        }
        return Math.min(Integer.MAX_VALUE, (int) index);
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Redis key must not be blank");
        }
    }

    private static double distanceInKilometers(Point first, Point second) {
        double lat1 = Math.toRadians(first.getY());
        double lat2 = Math.toRadians(second.getY());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(second.getX() - first.getX());
        double haversine = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0088d * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }
}
