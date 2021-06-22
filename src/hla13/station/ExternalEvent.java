package hla13.station;


import java.util.Comparator;

public class ExternalEvent {

    public enum EventType {CREATE_CLIENT, MOVE_TO_CASH_REGISTER_FROM_DISTRIBUTOR, MOVE_TO_CASH_REGISTER_FROM_CAR_WASH,
        MOVE_TO_CAR_WASH, GET}

    private  int id;
    private  int amountOfFuel;
    private String fuelType;
    private EventType eventType;
    private Double time;

    public ExternalEvent(int id, EventType eventType, Double time) {
        this.id = id;
        this.eventType = eventType;
        this.time = time;

    }

    public ExternalEvent(int id, String fuelType, int amountOfFuel, EventType eventType, Double time) {
        this.id = id;
        this.eventType = eventType;
        this.time = time;
        this.fuelType = fuelType;
        this.amountOfFuel = amountOfFuel;
    }

    public EventType getEventType() {
        return eventType;
    }

    public int getId() {
        return id;
    }

    public double getTime() {
        return time;
    }

    public int getAmountOfFuel() {
        return amountOfFuel;
    }

    public String getFuelType() {
        return fuelType;
    }

    public static class ExternalEventComparator implements Comparator<ExternalEvent> {

        @Override
        public int compare(ExternalEvent o1, ExternalEvent o2) {
            return o1.time.compareTo(o2.time);
        }
    }

}