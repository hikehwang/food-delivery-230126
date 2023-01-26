package fooddelivery.domain;

import fooddelivery.infra.AbstractEvent;
import lombok.Data;
import java.util.*;


@Data
public class DeliveryStarted extends AbstractEvent {

    private Long id;
    private Long orderID;
    private String storeID;
    private String menuID;
    private Integer qty;
    private String status;
    private String address;
}
