package hla13.producerConsumer.storage;


import java.util.Comparator;

public class ExternalEvent {

    public enum EventType {CREATE_CLIENT, MOVE_TO_CASH_REGISTER_FROM_DISTRIBUTOR, GET}

    private  int id;
    private EventType eventType;
    private Double time;

    public ExternalEvent(int id, EventType eventType, Double time) {
        this.id = id;
        this.eventType = eventType;
        this.time = time;

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

    public static class ExternalEventComparator implements Comparator<ExternalEvent> {

        @Override
        public int compare(ExternalEvent o1, ExternalEvent o2) {
            return o1.time.compareTo(o2.time);
        }
    }

}
