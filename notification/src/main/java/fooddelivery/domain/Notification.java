package fooddelivery.domain;

import fooddelivery.domain.Notified;
import fooddelivery.NotificationApplication;
import javax.persistence.*;
import java.util.List;
import lombok.Data;
import java.util.Date;


@Entity
@Table(name="Notification_table")
@Data

public class Notification  {


    
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    
    
    
    
    
    private Long id;
    
    
    
    
    
    private Long orderId;
    
    
    
    
    
    private String storeId;
    
    
    
    
    
    private String menuId;

    @PostPersist
    public void onPostPersist(){


        Notified notified = new Notified(this);
        notified.publishAfterCommit();

    }

    public static NotificationRepository repository(){
        NotificationRepository notificationRepository = NotificationApplication.applicationContext.getBean(NotificationRepository.class);
        return notificationRepository;
    }




    public static void notifyViaKakao(CookStarted cookStarted){

        /** Example 1:  new item 
        Notification notification = new Notification();
        repository().save(notification);

        */

        /** Example 2:  finding and process
        
        repository().findById(cookStarted.get???()).ifPresent(notification->{
            
            notification // do something
            repository().save(notification);


         });
        */

        
    }
    public static void notifyViaKakao(CoookFinished coookFinished){

        /** Example 1:  new item 
        Notification notification = new Notification();
        repository().save(notification);

        */

        /** Example 2:  finding and process
        
        repository().findById(coookFinished.get???()).ifPresent(notification->{
            
            notification // do something
            repository().save(notification);


         });
        */

        
    }
    public static void notifyViaKakao(DeliveryStarted deliveryStarted){

        /** Example 1:  new item 
        Notification notification = new Notification();
        repository().save(notification);

        */

        /** Example 2:  finding and process
        
        repository().findById(deliveryStarted.get???()).ifPresent(notification->{
            
            notification // do something
            repository().save(notification);


         });
        */

        
    }


}
