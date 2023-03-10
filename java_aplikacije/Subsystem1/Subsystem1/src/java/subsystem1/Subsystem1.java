/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package subsystem1;

import entities.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Resource;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.validation.ConstraintViolationException;


/**
 *
 * @author remax
 */
public class Subsystem1 {
    
    private static final byte CREATE_CITY = 1;
    private static final byte CREATE_USER = 2;
    private static final byte WIRE_MONEY_TO_USER = 3;
    private static final byte CHANGE_USER_ADDRESS = 4;
    private static final byte ALL_CITIES = 12;
    private static final byte ALL_USERS = 13;
    
    static EntityManagerFactory emf = Persistence.createEntityManagerFactory("Subsystem1PU");
    static EntityManager em = emf.createEntityManager();
    
    @Resource(lookup = "myConnFactory")
    static ConnectionFactory connFactory;
    
    @Resource(lookup="ZOCOVTOPIC")
    static Topic topic;
    
    @Resource(lookup="KKP")
    static Queue serverQueue;
    
    //kreirati po jednog proizvodjaca i jednog potrosaca za svaku vezu sa drugim podsistemom
    
    @Resource(lookup="subsystem1_subsystem2_queue")
    static Queue subsystem1_subsystem2_queue; // producer
    JMSProducer subsystem2_producer;
    
    @Resource(lookup="subsystem2_subsystem1_queue")
    static Queue subsystem2_subsystem1_queue; // consumer
    JMSConsumer subsystem2_consumer;
    
    @Resource(lookup="subsystem1_subsystem3_queue")
    static Queue subsystem1_subsystem3_queue; // producer
    JMSProducer subsystem3_producer;
    
    @Resource(lookup="subsystem3_subsystem1_queue")
    static Queue subsystem3_subsystem1_queue; // consumer
    JMSConsumer subsystem3_consumer;
    
    JMSContext context;
    JMSConsumer consumer;
    JMSProducer producer;
    //==========================================================================
    
    private TextMessage createUser(String userName, String firstName, String lastName, 
            String password, String streetName, String streetNumber, String cityName, String cityCountry) 
    {
        TextMessage textMessage = null;
        
        try {
            
            Korisnik user = new Korisnik();
            user.setKorisnickoIme(userName);
            user.setIme(firstName);
            user.setPrezime(lastName);
            user.setSifra(password);     
            user.setNovac(0);
            
            List<Korisnik> users = em.createNamedQuery("Korisnik.findByKorisnickoIme", Korisnik.class).
                setParameter("korisnickoIme", userName).getResultList();
        
            Korisnik userControlVar = (users.isEmpty()? null : users.get(0));
            
            
            List<Grad> cities = em.createNamedQuery("Grad.findByNazivDrzava", Grad.class).
                setParameter("naziv", cityName).
                setParameter("drzava", cityCountry).
                getResultList();
        
            Grad cityControlVar = (cities.isEmpty()? null : cities.get(0));
        
            List<Adresa> addressess = em.createNamedQuery("Adresa.findByUlicaBroj", Adresa.class).
                setParameter("ulica", streetName).
                setParameter("broj", Integer.parseInt(streetNumber)).
                getResultList();
        
            Adresa addressControlVar = (addressess.isEmpty()? null : addressess.get(0));
        
        
        
            String responseText = "";
            int returnStatus=0;
        
            if(cityControlVar == null) 
            {
                responseText = "Specified city is not in database";
                returnStatus = -1;
            }
            else if (addressControlVar == null) 
            {
                responseText = "Specified adress is not in database";
                returnStatus = -1;
            }
            else if (userControlVar!=null) 
            {
                responseText = "User is already in database";
                returnStatus = -1;
            }
            else 
            {
            
                user.setIdGrad(cityControlVar);
                user.setIdAdresa(addressControlVar);
            
            
                try {
                        em.getTransaction().begin();
                        em.persist(user);
                        em.getTransaction().commit();
                } catch (ConstraintViolationException e) {e.printStackTrace();}
                finally 
                {
                    if (em.getTransaction().isActive())
                        em.getTransaction().rollback();
                }
            
                TextMessage txtmsgSub2 = context.createTextMessage("sinhronizacija");
                txtmsgSub2.setByteProperty("request", CREATE_USER);
                txtmsgSub2.setStringProperty("username", userName);
                txtmsgSub2.setStringProperty("password", password);
          
                subsystem2_producer.send(subsystem1_subsystem2_queue, txtmsgSub2);
            
                TextMessage txtmsgSub3 = context.createTextMessage("sinhronizacija");
                txtmsgSub3.setByteProperty("request", CREATE_USER);
                txtmsgSub3.setStringProperty("username", userName);
                txtmsgSub3.setStringProperty("cityName", cityName);
                txtmsgSub3.setStringProperty("cityCountry", cityCountry);
                txtmsgSub3.setStringProperty("streetName", streetName);
                txtmsgSub3.setStringProperty("streetNumber", streetNumber);
            
                subsystem3_producer.send(subsystem1_subsystem3_queue, txtmsgSub3);
            
            }
            
            textMessage = context.createTextMessage(responseText);
            textMessage.setIntProperty("status", returnStatus);
            
            } catch (JMSException ex) {
            Logger.getLogger(Subsystem1.class.getName()).log(Level.SEVERE, null, ex);
            }
        
        return textMessage;
    }
    
    private TextMessage wireMoneyToUser(String username, float funds) 
    {
         TextMessage textMessage = null;
        try {
            
        
        List<Korisnik> users = em.createNamedQuery("Korisnik.findByKorisnickoIme", Korisnik.class).
                setParameter("korisnickoIme", username).
                getResultList();
        
        Korisnik user = (users.isEmpty()? null : users.get(0));
        
        
        String responseText = "";
        int returnStatus=0;
        
        if(user==null) 
        {
            responseText = "User does not exist";
            returnStatus = -1;
        }
        else 
        {
            
            user.setNovac(funds+user.getNovac());
            
            try {
                    em.getTransaction().begin();
                    em.persist(user);
                    em.getTransaction().commit();
            } catch (ConstraintViolationException e) {
                    e.printStackTrace();
            }
            finally 
            {
                 if (em.getTransaction().isActive())
                        em.getTransaction().rollback();
            }
            
                TextMessage txtmsgSub2 = context.createTextMessage("sinhronizacija");
                txtmsgSub2.setByteProperty("request", WIRE_MONEY_TO_USER);
                txtmsgSub2.setStringProperty("username", user.getKorisnickoIme());
                txtmsgSub2.setFloatProperty("newBalance", user.getNovac());
          
                subsystem2_producer.send(subsystem1_subsystem2_queue, txtmsgSub2);
                
                TextMessage txtmsgSub3 = context.createTextMessage("sinhronizacija");
                txtmsgSub3.setByteProperty("request", WIRE_MONEY_TO_USER);
                txtmsgSub3.setStringProperty("username", user.getKorisnickoIme());
                txtmsgSub3.setFloatProperty("newBalance", user.getNovac());
          
                subsystem2_producer.send(subsystem1_subsystem3_queue, txtmsgSub3);
            
            
        }
            
        textMessage = context.createTextMessage(responseText);
        textMessage.setIntProperty("status", returnStatus);
            
        } catch (JMSException ex) {
            Logger.getLogger(Subsystem1.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return textMessage;
    }
    
    private TextMessage updateUserAddress(String userName, String street, int streetNumber) 
    {
        TextMessage textMessage = null;
        try {
            
        
        List<Korisnik> users = em.createNamedQuery("Korisnik.findByKorisnickoIme", Korisnik.class).
                setParameter("korisnickoIme", userName).
                getResultList();
        
        Korisnik user = (users.isEmpty()? null : users.get(0));
        
        List<Adresa> addressess = em.createNamedQuery("Adresa.findByUlicaBroj", Adresa.class).
                setParameter("ulica", street).
                setParameter("broj", streetNumber).
                getResultList();
        
        Adresa address = (addressess.isEmpty()? null : addressess.get(0));
        
        
        String responseText = "";
        int returnStatus=0;
        
        if(user==null) 
        {
            responseText = "User does not exist";
            returnStatus = -1;
        }
        else if (address==null) 
        {
            responseText = "Address does not exist in database";
            returnStatus = -1;
        }
        else 
        {
            user.setIdAdresa(address);
     
            try {
                    em.getTransaction().begin();
                    em.persist(user);
                    em.getTransaction().commit();
            } catch (ConstraintViolationException e) {
                    e.printStackTrace();
            }
            finally 
            {
                 if (em.getTransaction().isActive())
                        em.getTransaction().rollback();
            }
            
                TextMessage txtmsgSub3 = context.createTextMessage("sinhronizacija");
                txtmsgSub3.setByteProperty("request", CHANGE_USER_ADDRESS);
                txtmsgSub3.setStringProperty("username", userName);
                txtmsgSub3.setStringProperty("street", street);
                txtmsgSub3.setIntProperty("streetNumber", streetNumber);
            
                subsystem3_producer.send(subsystem1_subsystem3_queue, txtmsgSub3);
            
            
        }
            
        textMessage = context.createTextMessage(responseText);
        textMessage.setIntProperty("status", returnStatus);
            
        } catch (JMSException ex) {
            Logger.getLogger(Subsystem1.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return textMessage;
    }
    
    private ObjectMessage getCities() {
        
        List<Grad> cities = em.createNamedQuery("Grad.findAll", Grad.class).getResultList();
        
        ArrayList<String> stringCities = new ArrayList<>();
        
        for (Grad city : cities) 
            stringCities.add(city.getNaziv() + "|" + city.getDrzava());

        
        return context.createObjectMessage(stringCities);
        
    }
    
    private ObjectMessage getUsers() 
    {
        
        List<Korisnik> users = em.createNamedQuery("Korisnik.findAll", Korisnik.class).getResultList();
        
        ArrayList<String> usersString = new ArrayList<>();
        
        for (Korisnik user : users) 
            usersString.add(user.getKorisnickoIme() + "|" + user.getIme() + "|" 
                    + user.getPrezime() + "|"  + user.getNovac());
        
        return context.createObjectMessage(usersString);
    }
    
    private TextMessage createCity(String cityName, String cityCountry) 
    {
        TextMessage textMessage = null;
        try {
            
            Grad city = new Grad();
            city.setNaziv(cityName);
            city.setDrzava(cityCountry);
        
        List<Grad> cities = em.createNamedQuery("Grad.findByNazivDrzava", Grad.class).
                setParameter("naziv", cityName).
                setParameter("drzava", cityCountry).
                getResultList();
        
        Grad controlVar = (cities.isEmpty()? null : cities.get(0));
        
        
        String responseText = "";
        int returnStatus=0;
        
        if(controlVar!=null) 
        {
            responseText = "City in specified country is already in databse";
            returnStatus = -1;
        }
        else 
        {
            try {
                    em.getTransaction().begin();
                    em.persist(city);
                    em.getTransaction().commit();
            } catch (EntityExistsException e) {
            responseText = "City in specified country is already in databse";
            returnStatus = -1;
            }
            finally 
            {
                 if (em.getTransaction().isActive())
                        em.getTransaction().rollback();
            }
            
            //Salje se informacija podsistemu3 da treba da kreira grad u svojoj bazi
            
            TextMessage txtmsg = context.createTextMessage("sinhronizacija");
            txtmsg.setByteProperty("request", CREATE_CITY);
            txtmsg.setStringProperty("cityName", cityName);
            txtmsg.setStringProperty("cityCountry", cityCountry);
            
            subsystem3_producer.send(subsystem1_subsystem3_queue, txtmsg);
            
        }
            
        textMessage = context.createTextMessage(responseText);
        textMessage.setIntProperty("status", returnStatus);
            
        } catch (JMSException ex) {
            Logger.getLogger(Subsystem1.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return textMessage;
    }
    
    private void decreaseUserBalance(String username, float moneyToDecrease) 
    {
        Korisnik user = em.createNamedQuery("Korisnik.findByKorisnickoIme", Korisnik.class).
                setParameter("korisnickoIme", username).
                getResultList().get(0);
        
        user.setNovac(user.getNovac()-moneyToDecrease);
        
        try {
                    em.getTransaction().begin();
                    em.persist(user);
                    em.getTransaction().commit();
            } catch (ConstraintViolationException e) {
                    e.printStackTrace();
            }
            finally 
            {
                 if (em.getTransaction().isActive())
                        em.getTransaction().rollback();
            }
    }

    private void subsystem2Listener(Message msg) 
    {
        
    }
    
    private void subsystem3Listener(Message msg) 
    {
        
        
         try {
            switch(msg.getByteProperty("request"))
            {
                case 3: //DECREASE_USER_BALLANCE in subsystem3
                    String monneyUpdate = msg.getStringProperty("moneyToUpdate");
                    String username = msg.getStringProperty("username");
            
                    decreaseUserBalance(username, Float.parseFloat(monneyUpdate));
                break;
            }
        } catch (JMSException ex) {
            Logger.getLogger(Subsystem1.class.getName()).log(Level.SEVERE, null, ex);
        }
 
    }
    
    private void run() 
    {
        String msgSelector = "podsistem=1";
        
        context = connFactory.createContext();
        context.setClientID("1");
        consumer = context.createDurableConsumer(topic, "sub1", msgSelector, false);
        producer = context.createProducer();
        
        
        subsystem2_producer = context.createProducer();
        subsystem2_consumer = context.createConsumer(subsystem2_subsystem1_queue);
        subsystem2_consumer.setMessageListener((Message msg) -> { subsystem2Listener(msg); });
        
        subsystem3_producer = context.createProducer();
        subsystem3_consumer = context.createConsumer(subsystem3_subsystem1_queue);
        subsystem3_consumer.setMessageListener((Message msg) -> { subsystem3Listener(msg); });
        
        
        
        String cityName, cityCountry, username, userFirstName, userLastName,
                streetName, streetNumber, userPassword, money;
        
        while (true) 
        {
            try {
                System.out.println("Podsistem1: Cekanje zahteva...");
                
                
                TextMessage textMessage = (TextMessage) consumer.receive();
                byte request = textMessage.getByteProperty("request");

                Message response = null;
                
                switch(request) {
                
                    case CREATE_CITY:
                        
                        cityName = textMessage.getStringProperty("cityName");
                        cityCountry = textMessage.getStringProperty("cityCountry");
                        response = createCity(cityName, cityCountry);
                        break;
                        
                    case ALL_CITIES:
                        response = getCities();
                        break;
                    case ALL_USERS:
                        response = getUsers();
                        break;
                    case CREATE_USER:
                        
                        username = textMessage.getStringProperty("userName");
                        userFirstName = textMessage.getStringProperty("firstName");
                        userLastName = textMessage.getStringProperty("lastName");
                        userPassword = textMessage.getStringProperty("password");
                        streetName = textMessage.getStringProperty("street");
                        streetNumber = textMessage.getStringProperty("streetNumber");
                        cityName = textMessage.getStringProperty("cityName");
                        cityCountry = textMessage.getStringProperty("cityCountry");
      
                        
                        response=createUser(username, userFirstName, userLastName, 
                                userPassword, streetName, streetNumber, cityName, cityCountry);
                        
                        
                        break;
                    case WIRE_MONEY_TO_USER:
                        
                        username = textMessage.getStringProperty("userName");
                        money = textMessage.getStringProperty("money");
                      
                        response=wireMoneyToUser(username, Float.parseFloat(money));
                        
                        break;
                    case CHANGE_USER_ADDRESS:
                        username = textMessage.getStringProperty("userName");
                        streetName = textMessage.getStringProperty("street");
                        streetNumber = textMessage.getStringProperty("streetNumber");
                        
                        response = updateUserAddress(username, streetName, Integer.parseInt(streetNumber));
                        
                        break;    
                }
                
                producer.send(serverQueue, response);
                
            } catch (JMSException ex) {
                Logger.getLogger(Subsystem1.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    public static void main(String[] args) {
        
        Subsystem1 sub1 = new Subsystem1();
        sub1.run();
    }
    
}
