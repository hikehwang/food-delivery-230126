![image](https://user-images.githubusercontent.com/487999/79708354-29074a80-82fa-11ea-80df-0db3962fb453.png)

# 서비스 시나리오

배달의 민족 : 마이크로서비스 분석/설계 및 구현


기능적 요구사항
1. 고객이 메뉴를 선택하여 주문한다.
1. 고객이 선택한 메뉴에 대해 결제한다.
1. 주문이 되면 주문 내역이 입점상점주인에게 주문정보가 전달된다.
1. 상점주는 주문을 수락하거나 거절할 수 있다.
1. 상점주는 요리시작때와 완료 시점에 시스템에 상태를 입력한다.
1. 고객은 아직 요리가 시작되지 않은 주문은 취소할 수 있다.
1. 요리가 완료되면 고객의 지역 인근의 라이더들에 의해 배송건 조회가 가능하다.
1. 라이더가 해당 요리를 Pick한 후, 앱을 통해 통보한다.
1. 고객이 주문상태를 중간중간 조회한다.
1. 주문상태가 바뀔 때 마다 카톡으로 알림을 보낸다.
1. 라이더의 배달이 끝나면 배송확인 버튼으로 모든 거래가 완료된다.


비기능적 요구사항
1. 장애격리
    1. 상점관리 기능이 수행되지 않더라도 주문은 365일 24시간 받을 수 있어야 한다  Async (event-driven), Eventual Consistency
    1. 결제시스템이 과중되면 사용자를 잠시동안 받지 않고 결제를 잠시후에 하도록 유도한다  Circuit breaker, fallback
1. 성능
    1. 고객이 자주 상점관리에서 확인할 수 있는 배달상태를 주문시스템(프론트엔드)에서 확인할 수 있어야 한다  CQRS
    1. 배달상태가 바뀔때마다 카톡 등으로 알림을 줄 수 있어야 한다  Event driven


# 체크포인트

1. Saga (Pub / Sub)
2. CQRS
3. Compensation / Correlation


# 분석/설계


## AS-IS 조직 (Horizontally-Aligned)
  ![image](https://user-images.githubusercontent.com/487999/79684144-2a893200-826a-11ea-9a01-79927d3a0107.png)

## TO-BE 조직 (Vertically-Aligned)
  ![image](https://user-images.githubusercontent.com/487999/79684159-3543c700-826a-11ea-8d5f-a3fc0c4cad87.png)

## Event Storming 결과
  ![image](https://user-images.githubusercontent.com/80295023/214212164-7e7fb651-9a47-42b3-b444-29120739a0fe.png)


# 구현:

분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트와 Java로 구현하였다. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 808n 이다)

```
cd frontend
mvn spring-boot:run

cd store
mvn spring-boot:run 

cd notification
mvn spring-boot:run  

cd rider
mvn spring-boot:run

cd history
mvn spring-boot:run
```

# 체크포인트 구현:


## 1. Saga (Pub / Sub)
kafka를 통한 Pub/Sub 비동기 통신
- Publish 예제 코드
```
    @PostPersist
    public void onPostPersist() {
        OrderPlaced orderPlaced = new OrderPlaced(this);
        orderPlaced.publishAfterCommit();

        OrderCanceled orderCanceled = new OrderCanceled(this);
        orderCanceled.publishAfterCommit();
    }    
```
- Subscribe 예제 코드
```
@Service
@Transactional
public class PolicyHandler {

    @Autowired
    StoreOrdersRepository storeOrdersRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString) {}

    @StreamListener(
        value = KafkaProcessor.INPUT,
        condition = "headers['type']=='OrderPlaced'"
    )
    public void wheneverOrderPlaced_OrderInfoTransferred(
        @Payload OrderPlaced orderPlaced
    ) {
        OrderPlaced event = orderPlaced;
        System.out.println(
            "\n\n##### listener OrderInfoTransferred : " + orderPlaced + "\n\n"
        );

        // Sample Logic //
        StoreOrders.orderInfoTransferred(event);
    }

    @StreamListener(
        value = KafkaProcessor.INPUT,
        condition = "headers['type']=='OrderCanceled'"
    )
    public void wheneverOrderCanceled_CancelCooking(
        @Payload OrderCanceled orderCanceled
    ) {
        OrderCanceled event = orderCanceled;
        System.out.println(
            "\n\n##### listener CancelCooking : " + orderCanceled + "\n\n"
        );
        // Sample Logic //

    }
}
```

## 2. CQRS
dashboard를 통한 오더상태 업데이트 정보 조회
- Status Table
```
package fooddelivery.domain;

import java.util.Date;
import java.util.List;
import javax.persistence.*;
import lombok.Data;

@Entity
@Table(name = "Status_table")
@Data
public class Status {

    @Id
    //@GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;

    private String orderId;
    private String userId;
    private String storeId;
    private String menuId;
    private String orderStatus;
    private String storeStatus;
    private String delieveryStatus;
}
```
- Status View Handler
```
package fooddelivery.infra;

import fooddelivery.config.kafka.KafkaProcessor;
import fooddelivery.domain.*;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class StatusViewHandler {

    @Autowired
    private StatusRepository statusRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrderPlaced_then_CREATE_1(
        @Payload OrderPlaced orderPlaced
    ) {
        try {
            if (!orderPlaced.validate()) return;

            // view 객체 생성
            Status status = new Status();
            // view 객체에 이벤트의 Value 를 set 함
            status.setOrderId(String.valueOf(orderPlaced.getId()));
            status.setUserId(orderPlaced.getUserId());
            status.setMenuId(orderPlaced.getMenuId());
            status.setStoreId(orderPlaced.getStoreId());
            // view 레파지 토리에 save
            statusRepository.save(status);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrderAcepted_then_UPDATE_1(
        @Payload OrderAcepted orderAcepted
    ) {
        try {
            if (!orderAcepted.validate()) return;
            // view 객체 조회

            List<Status> statusList = statusRepository.findByOrderId(
                orderAcepted.getOrderId()
            );
            for (Status status : statusList) {
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                status.setOrderStatus("Order Accepted");
                // view 레파지 토리에 save
                statusRepository.save(status);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrderRejected_then_UPDATE_2(
        @Payload OrderRejected orderRejected
    ) {
        try {
            if (!orderRejected.validate()) return;
            // view 객체 조회

            List<Status> statusList = statusRepository.findByOrderId(
                orderRejected.getOrderId()
            );
            for (Status status : statusList) {
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                status.setOrderStatus("Order Rejected");
                // view 레파지 토리에 save
                statusRepository.save(status);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenCookStarted_then_UPDATE_3(
        @Payload CookStarted cookStarted
    ) {
        try {
            if (!cookStarted.validate()) return;
            // view 객체 조회

            List<Status> statusList = statusRepository.findByOrderId(
                cookStarted.getOrderId()
            );
            for (Status status : statusList) {
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                status.setStoreStatus("Cook Started");
                // view 레파지 토리에 save
                statusRepository.save(status);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenCoookFinished_then_UPDATE_4(
        @Payload CoookFinished coookFinished
    ) {
        try {
            if (!coookFinished.validate()) return;
            // view 객체 조회

            List<Status> statusList = statusRepository.findByOrderId(
                coookFinished.getOrderId()
            );
            for (Status status : statusList) {
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                status.setStoreStatus("Cook Finished");
                // view 레파지 토리에 save
                statusRepository.save(status);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenDeliveryStarted_then_UPDATE_5(
        @Payload DeliveryStarted deliveryStarted
    ) {
        try {
            if (!deliveryStarted.validate()) return;
            // view 객체 조회

            List<Status> statusList = statusRepository.findByOrderId(
                deliveryStarted.getOrderId()
            );
            for (Status status : statusList) {
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                status.setDelieveryStatus("Delivery Started");
                // view 레파지 토리에 save
                statusRepository.save(status);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenDeliveryFinished_then_UPDATE_6(
        @Payload DeliveryFinished deliveryFinished
    ) {
        try {
            if (!deliveryFinished.validate()) return;
            // view 객체 조회

            List<Status> statusList = statusRepository.findByOrderId(
                deliveryFinished.getOrderId()
            );
            for (Status status : statusList) {
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                status.setDelieveryStatus("Delivery Finished");
                // view 레파지 토리에 save
                statusRepository.save(status);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrderCanceled_then_UPDATE_7(
        @Payload OrderCanceled orderCanceled
    ) {
        try {
            if (!orderCanceled.validate()) return;
            // view 객체 조회

            List<Status> statusList = statusRepository.findByOrderId(
                String.valueOf(orderCanceled.getId())
            );
            for (Status status : statusList) {
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                status.setOrderStatus("Order Cancelled");
                // view 레파지 토리에 save
                statusRepository.save(status);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenDeliveryFinished_then_DELETE_1(
        @Payload DeliveryFinished deliveryFinished
    ) {
        try {
            if (!deliveryFinished.validate()) return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## 3. Compensation / Correlation
```
    public static void cancelCooking(OrderCanceled orderCanceled) {
        /** Example 1:  new item 
        StoreOrders storeOrders = new StoreOrders();
        repository().save(storeOrders);

        */

        /** Example 2:  finding and process
        
        repository().findById(orderCanceled.get???()).ifPresent(storeOrders->{
            
            storeOrders // do something
            repository().save(storeOrders);


         });
        */

        repository().findById(orderCanceled.getOrderId()).ifPresent(storeOrders->{
            storeOrders.setStatus("Order Cancelled");
            repository().save(storeOrders);
        });

    }
```    
