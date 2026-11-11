package org.example.back_end_iot.api;

import jakarta.persistence.criteria.Join;
import org.example.back_end_iot.entity.ActionHistory;
import org.example.back_end_iot.entity.Sensor;
import org.example.back_end_iot.entity.SensorData;
import org.example.back_end_iot.enums.ActionStatus;
import org.example.back_end_iot.enums.DeviceAction;
import org.example.back_end_iot.repository.ActionHistoryRepository;
import org.example.back_end_iot.repository.SensorDataRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.back_end_iot.realtime.RealtimeEvents.SensorSnapshot;

@RestController
@RequestMapping("/api")
public class ReadController {
    private static final List<String> SENSOR_KEYS = List.of("temperature", "humidity", "light");

    private final SensorDataRepository readings;
    private final ActionHistoryRepository actions;

    public ReadController(SensorDataRepository readings, ActionHistoryRepository actions) {
        this.readings = readings;
        this.actions = actions;
    }

    @GetMapping("/readings/latest")
    @Transactional(readOnly = true)
    public Map<String, ReadingView> latest() {
        Map<String, ReadingView> latest = new LinkedHashMap<>();
        for (String key : SENSOR_KEYS) {
            readings.findFirstBySensor_SensorKeyOrderByCreatedAtDescIdDesc(key)
                    .ifPresent(reading -> latest.put(key, readingView(reading)));
        }
        return latest;
    }

    @GetMapping("/sensor-data/latest")
    @Transactional(readOnly = true)
    public SensorSnapshot latestSnapshot() {
        Map<String, ReadingView> values = latest();
        LocalDateTime time = values.values().stream().map(ReadingView::createdAt)
                .max(LocalDateTime::compareTo).orElse(null);
        return new SensorSnapshot(values.containsKey("temperature") ? values.get("temperature").value() : null,
                values.containsKey("humidity") ? values.get("humidity").value() : null,
                values.containsKey("light") ? values.get("light").value() : null, time);
    }

    @GetMapping("/sensor-data/chart")
    @Transactional(readOnly = true)
    public Map<String, List<ChartPoint>> chart(@RequestParam(defaultValue = "20") int limit) {
        PageRequest request = pageRequest(0, limit, "desc");
        Map<String, List<ChartPoint>> result = new LinkedHashMap<>();
        for (String key : SENSOR_KEYS) {
            List<ChartPoint> points = new ArrayList<>(readings.findBySensor_SensorKey(key, request).stream()
                    .map(value -> new ChartPoint(value.getCreatedAt(), value.getValue())).toList());
            Collections.reverse(points);
            result.put(key, points);
        }
        return result;
    }

    @GetMapping({"/readings", "/sensor-data/history"})
    @Transactional(readOnly = true)
    public PageView<ReadingView> readings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sensorKey,
            @RequestParam(required = false) Integer sensorId,
            @RequestParam(required = false) Double value,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String order) {
        validateRange(from, to);
        if (sensorKey != null && !SENSOR_KEYS.contains(sensorKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã cảm biến không hợp lệ");
        }
        if (value != null && !Double.isFinite(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá trị cảm biến không hợp lệ");
        }
        Specification<SensorData> filter = (root, query, cb) -> cb.conjunction();
        if (sensorId != null) filter = filter.and((root, query, cb) -> cb.equal(root.join("sensor").get("id"), sensorId));
        if (from != null) filter = filter.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        if (to != null) filter = filter.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        if (sensorKey != null) {
            filter = filter.and((root, query, cb) -> {
                Join<SensorData, Sensor> sensor = root.join("sensor");
                return cb.equal(sensor.get("sensorKey"), sensorKey);
            });
        }
        if (value != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("value"), value));
        }
        if (before != null) {
            filter = filter.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), before));
        }
        return pageView(readings.findAll(filter, pageRequest(page, size, sortOrder(sort, order))).map(ReadController::readingView));
    }

    @GetMapping({"/history", "/devices/action-history"})
    @Transactional(readOnly = true)
    public PageView<HistoryView> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String deviceKey,
            @RequestParam(required = false) Integer deviceId,
            @RequestParam(required = false) DeviceAction action,
            @RequestParam(required = false) ActionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String order) {
        validateRange(from, to);
        Specification<ActionHistory> filter = (root, query, cb) -> cb.conjunction();
        if (deviceId != null) filter = filter.and((root, query, cb) -> cb.equal(root.join("device").get("id"), deviceId));
        if (from != null) filter = filter.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        if (to != null) filter = filter.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        if (deviceKey != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.join("device").get("deviceKey"), deviceKey));
        }
        if (action != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (status == ActionStatus.FAILED) {
            filter = filter.and((root, query, cb) -> root.get("status").in(ActionStatus.FAILED, ActionStatus.TIMEOUT));
        } else if (status != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (before != null) {
            filter = filter.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), before));
        }
        return pageView(actions.findAll(filter, pageRequest(page, size, sortOrder(sort, order))).map(ReadController::historyView));
    }

    private static void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from phải nhỏ hơn hoặc bằng to");
    }

    private static String sortOrder(String sort, String fallback) {
        if (sort == null) return fallback;
        if (sort.matches("(createdAt|created_at),(asc|desc)")) return sort.substring(sort.indexOf(',') + 1);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sort phải là createdAt,asc hoặc createdAt,desc");
    }

    private static PageRequest pageRequest(int page, int size, String order) {
        if (page < 0 || size < 1 || size > 100 || (!"asc".equals(order) && !"desc".equals(order))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang, kích thước hoặc thứ tự không hợp lệ");
        }
        Sort.Direction direction = "asc".equals(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, "createdAt", "id"));
    }

    private static ReadingView readingView(SensorData reading) {
        Sensor sensor = reading.getSensor();
        return new ReadingView(reading.getId(), sensor.getId(), sensor.getName(), sensor.getSensorKey(), sensor.getName(),
                sensor.getUnit(), reading.getValue(), reading.getCreatedAt());
    }

    private static HistoryView historyView(ActionHistory action) {
        ActionStatus status = action.getStatus() == ActionStatus.TIMEOUT ? ActionStatus.FAILED : action.getStatus();
        return new HistoryView(action.getId(), action.getDevice().getId(), action.getUser().getUsername(),
                action.getDevice().getDeviceKey(), action.getDevice().getName(),
                action.getAction(), status, action.getCreatedAt());
    }

    private static <T> PageView<T> pageView(Page<T> page) {
        return new PageView<>(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize());
    }

    public record ReadingView(Integer id, Integer sensorId, String sensorName, String sensorKey, String name, String unit,
                              Double value, LocalDateTime createdAt) { }

    public record HistoryView(Integer id, Integer deviceId, String username, String deviceKey, String deviceName,
                              DeviceAction action, ActionStatus status, LocalDateTime createdAt) { }

    public record ChartPoint(LocalDateTime time, Double value) { }
    public record PageView<T>(List<T> items, long total, int page, int size) {
        @JsonProperty public List<T> content() { return items; }
        @JsonProperty public long totalElements() { return total; }
        @JsonProperty public int totalPages() { return (int) Math.ceil((double) total / size); }
        @JsonProperty public int number() { return page; }
    }
}
