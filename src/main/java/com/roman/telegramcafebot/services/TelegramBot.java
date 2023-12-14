package com.roman.telegramcafebot.services;

import com.roman.telegramcafebot.config.BotConfig;
import com.roman.telegramcafebot.models.*;
import com.roman.telegramcafebot.repositories.*;
import com.roman.telegramcafebot.utils.*;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@AllArgsConstructor
public class TelegramBot extends TelegramLongPollingBot {

    private KeyboardMarkup keyboardMarkup;

    private AdminPassowrdRepository adminPasswordRepository;
    private final BotConfig botConfig;
    private Reservation reservation;

    private Button button;

    private CoworkerRepository coworkerRepository;

    private ButtonRepository buttonRepository;

    private OrderRepository orderRepository;

    private MenuItemRepository menuItemRepository;

    private Cart cart;
    @Autowired
    public TelegramBot(Reservation reservation, BotConfig botConfig, KeyboardMarkup keyboardMarkup,
                       AdminPassowrdRepository adminPasswordRepository, ButtonRepository buttonRepository,
                       CoworkerRepository coworkerRepository, OrderRepository orderRepository,
                       Button button, MenuItemRepository menuItemRepository,
                       Cart cart){
        this.reservation = reservation;
        this.botConfig = botConfig;
        this.keyboardMarkup = keyboardMarkup;
        this.adminPasswordRepository = adminPasswordRepository;
        this.buttonRepository = buttonRepository;
        this.coworkerRepository = coworkerRepository;
        this.orderRepository = orderRepository;
        this.button = button;
        this.menuItemRepository = menuItemRepository;
        this.cart = cart;
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @Override
    public void onRegister() {
        super.onRegister();
    }

    @Override
    public void onUpdateReceived(Update update) {

        if(update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            switch (messageText) {
                case "/start" -> {
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                }
                case "/adminoff" -> {
                    Coworker coworker = coworkerRepository.findCoworkerByChatId(String.valueOf(chatId));
                    if(coworker != null){
                        coworker.setActive(false);
                        coworkerRepository.save(coworker);
                    }
                }
            }
            if(messageText.startsWith("/reservation")) {
                reservation.setName(messageText.substring(10));
                String text = "Теперь нужно выбрать время. Что бы это сделать введите" +
                        " /time и время которые на которое вы хотите забронировать столик. Напримет */time 14.30*";
                sendMessage(chatId, text);
            } else if (messageText.startsWith("/time")) {
                reservation.setTime(messageText.substring(5));
                reservation.setCustomerChatId(chatId);
                String text = "Запрос на бронь отправлен нашему сотруднику, вы получите подтверждение как только " +
                        "сотрудник подтвердит бронь";
                sendMessage(chatId, text);
                sendMessage(getCoworkerChatId(), reservation.toString(), createOneButton("Подтвердить бронь стола", "RESERVATION_CONFIRMED"));
            } else if (messageText.startsWith("/password")) {
                String password = messageText.substring(10);
                String passwordFromDB = Objects.requireNonNull(adminPasswordRepository.findById(1).orElse(null)).getKey();
                if(password.equals(passwordFromDB)){
                    Coworker coworker = new Coworker();
                    coworker.setChatId(String.valueOf(chatId));
                    coworker.setActive(true);
                    coworkerRepository.save(coworker);
                    sendMessage(chatId, "Ключ активирован");
                }
            }
            else if (messageText.startsWith("/newitem")){
                if(validateCoworker(chatId)){
                    createMenuItem(messageText);
                    sendMessage(getCoworkerChatId(), "К какому разделу относиться?",
                            keyboardMarkup.getKeyboardMarkup(getButtons("adminmenu"), 3));
                }
                else sendMessage(chatId, "Не добавлено в корзину, у вас нет доступа к этой функции");
            }
            else if (messageText.startsWith("/deleteitem")){
                if(validateCoworker(chatId)){
                    deleteMenuItem(messageText);
                    sendMessage(chatId, "Удалено успешно", getGoToMenuButton());
                }
                else sendMessage(chatId, "Не удалено, у вас нет доступа к этой функции");
            }
        }
        else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            if (callbackData.startsWith("TYPE")){
                createButtonFromMenuItem(setMenuItemsMenuBelonging(callbackData));
                sendMessage(getCoworkerChatId(), "Добавлено успешно", getGoToMenuButton());
            }
            else if (callbackData.startsWith("TABLE_")){
                int tableNumber = Integer.parseInt(callbackData.substring(6));
                reservation.setTable(tableNumber);
                String text = "Вы выбрали стол " + tableNumber + " теперь нужно ввести имя на которое вы хотите" +
                        "забронировать столик. Введите /reservation  далее ваше имя например /reservation Роман";
                sendMessage(chatId, text);
            }
            else if (callbackData.startsWith("REMOVEFROMCART")){
                removeFromCart(callbackData);
                sendMessage(chatId, "Удалено из корзины", getGoToMenuButton());
            }
            else if(callbackData.startsWith("ITEM")){
                sendMessage(chatId, createConfirmationText(findItemById(callbackData)),
                        keyboardMarkup.getKeyboardMarkup(getConfirmationButtons("confirmationmenu", callbackData), 2));
            }
            else if(callbackData.startsWith("ADDTOCART")){
                addToCart(chatId, callbackData);
                sendMessage(chatId,"Добавлено успешно", getGoToMenuButton());
            }

            switch (callbackData) {
                case "RESERVATION" -> {
                    reservation = new Reservation();
                    String text = "Введите номер стола: ";
                    sendMessage(chatId, text, keyboardMarkup.getKeyboardMarkup(getButtons("tablemenu"), 4));
                }
                case "FOODMENU" ->
                    sendMessage(chatId, "Menu", keyboardMarkup.getKeyboardMarkup(getButtons("foodmenu"), 3));

                case "RESERVATION_CONFIRMED" -> {
                    String text = "Бронь стола подтверждена";
                    sendMessage(chatId, text);
                }
                case "BREAKFASTS" -> {
                    sendMessage(chatId, getMenuText(getItemsByMenuBelonging("breakfastmenu")),
                            keyboardMarkup.getKeyboardMarkup(sortButtonsByName(getButtons("breakfastmenu")), 4));
                }
                case "CROISSANTS" -> {
                    sendMessage(chatId, getMenuText(getItemsByMenuBelonging("croissantmenu")),
                            keyboardMarkup.getKeyboardMarkup(getButtons("croissantmenu"), 4));
                }
                case "ROMANPIZZAS" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("romanpizzamenu")),
                            keyboardMarkup.getKeyboardMarkup(sortButtonsByName(getButtons("romanpizzamenu")), 4));
                }
                case "HOTFOOD" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("hotfoodmenu")),
                            keyboardMarkup.getKeyboardMarkup(getButtons("hotfoodmenu"), 4));
                }
                case "PIESCHUDU" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("pieschudumenu")),
                            keyboardMarkup.getKeyboardMarkup(getButtons("pieschudumenu"), 1));
                }
                case "SOUPS" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("soupmenu")),
                            keyboardMarkup.getKeyboardMarkup(getButtons("soupmenu"), 4));
                }
                case "SALADS" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("saladmenu")),
                            keyboardMarkup.getKeyboardMarkup(getButtons("saladmenu"), 4));
                }
                case "SANDWICHES" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("sandwichmenu")),
                            keyboardMarkup.getKeyboardMarkup(getButtons("sandwichmenu"), 4));
                }
                case "BRUSCHETTAS" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("bruschettamenu")),
                            keyboardMarkup.getKeyboardMarkup(getButtons("bruschettamenu"), 4));
                }
                case "ADDITION" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("additionmenu")),
                            keyboardMarkup.getKeyboardMarkup(getButtons("additionmenu"), 4));
                }
                case "DRINKS" -> {
                    sendMessage(chatId,"Напитки", keyboardMarkup.getKeyboardMarkup(getButtons("drinkmenu"), 2));
                }
                case "DESERTS" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("desertmenu")),
                            keyboardMarkup.getKeyboardMarkup(getButtons("desertmenu"), 2));
                }
                case "BREAD" -> {
                    sendMessage(chatId,"Римские пиццы", keyboardMarkup.getKeyboardMarkup(getButtons("breadmenu"), 2));
                }
                case "TEA" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("teamenu")),
                            keyboardMarkup.getKeyboardMarkup(getButtons("teamenu"), 4));
                }
                case "COFFEE" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("coffeemenu"))
                            , keyboardMarkup.getKeyboardMarkup(getButtons("coffemenu"), 4));
                }
                case "CACAO" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("cacaomenu")),
                            keyboardMarkup.getKeyboardMarkup(getButtons("cacaomenu"),4 ));
                }
                case "SIGNATUREDRINKS" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("signaturedrinksmenu")),
                            keyboardMarkup.getKeyboardMarkup(getButtons("signaturedrinksmenu"), 4));
                }
                case "NOTTEA" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("notteamenu")),
                            keyboardMarkup.getKeyboardMarkup(getButtons("notteamenu"), 4));
                }
                case "DRINKSADDITION" -> {
                    sendMessage(chatId,getMenuText(getItemsByMenuBelonging("drinksadditionmenu")),
                            keyboardMarkup.getKeyboardMarkup(getButtons("drinksadditionmenu"), 4));
                }

                case "GOTOPAYMENT" -> {
                    String text = "Для оплаты нужно перевести " + cart.getTotalPrice() + " рублей на карту 1234 3456 " +
                            "2345 4556 или на телефон 9485749284 далее нажмите на кнопку олпачено и после этого" +
                            "информация попадет к нашему сотруднику";
                    sendMessage(chatId, text, createOneButton("Подтвердить", "PAYMENTCONFIRMED"));
                }
                case "PAYMENTCONFIRMED" -> {
                    Order order = createOrder(cart);
                    sendMessage(chatId, "Благодарим за покупку");
                    Long coworkerChatId = getCoworkerChatId();
                    sendMessage(coworkerChatId, order.toString());
                }
                case "SHOWCART" -> {
                    sendMessage(chatId, getMenuTextForCart(cart.getMenuItemList()), keyboardMarkup.getCartKeyBoardMarkup(cart));
                }
                case "REMOVEALLFROMCART" -> {
                    cart.resetCart();
                    sendMessage(chatId, "Корзина очищина", getGoToMenuButton());
                }

            }
        }
    }

    @Override
    public void onUpdatesReceived(List<Update> updates) {
        super.onUpdatesReceived(updates);
    }

    private void startCommandReceived(Long chatId, String name) {
        String answer = "Приветствую, " + name;
        sendMessage(chatId, answer, keyboardMarkup.getKeyboardMarkup(getButtons("mainmenu"), 1));
    }

    private void sendMessage(Long chatId, String textToSend, InlineKeyboardMarkup keyboardMarkup) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(textToSend);
        sendMessage.setReplyMarkup(keyboardMarkup);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendMessage(Long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(textToSend);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendMessage(Long chatId, InlineKeyboardMarkup keyboardMarkup) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setReplyMarkup(keyboardMarkup);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private InlineKeyboardMarkup createOneButton(String text, String callbackDATA){
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackDATA);

        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        rowInLine.add(button);
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        rowsInLine.add(rowInLine);
        keyboardMarkup.setKeyboard(rowsInLine);
        return keyboardMarkup;
    }
    private void saveOrder(Order order){
        orderRepository.save(order);
    }
    private InlineKeyboardMarkup getGoToMenuButton(){
        return createOneButton("Перейти в меню", "FOODMENU");
    }
    private String getItemsName(String itemInfo){
        Pattern p = Pattern.compile("\"([^\"]*)\"");
        Matcher m = p.matcher(itemInfo);
        m.find();
        return m.group().replace("\"", "");
    }
    private String getItemsPrice(String itemInfo){
        String [] info = itemInfo.split(" ");
        return info[info.length-1];
    }
    private boolean validateCoworker(Long chatId){
        Coworker coworker = coworkerRepository.findCoworkerByChatId(chatId.toString());
        if(coworker == null){
            return false;
        }
        else return true;
    }
    private Long getCoworkerChatId(){
        Coworker coworker = coworkerRepository.findCoworkerByIsActive(true);
        return Long.valueOf(coworker.getChatId());
    }
    private String getMenuText(List<MenuItem> items){
        StringBuilder menuText = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            menuText.append(i+1 + ". " + items.get(i).getName() + " " + items.get(i).getPrice() + " руб." + "\n");
        }
        return menuText.toString();
    }

    private String getMenuTextForCart(List<MenuItem> items){
        if(items.isEmpty()) {
            return "Корзина пуста";
        }
        StringBuilder menuText = new StringBuilder();
        String cartMenuText = "Что бы удалить предмет из корзины, просто кликните по номеру который соответствует товару" + "\n" + "\n";
        menuText.append(cartMenuText);
        int sum = 0;
        for (int i = 0; i < items.size(); i++) {
            menuText.append(i + 1 + ". " + items.get(i).getName() + " " + items.get(i).getPrice() + " руб." + "\n");
            sum += items.get(i).getPrice();
        }
        String infoAboutSum = "\n" + "Сумма корзины " + sum;
        menuText.append(infoAboutSum);
        return menuText.toString();
    }
    private List<Button> sortButtonsByName(List<Button> buttonsToSort){
        List<Button> buttons = buttonsToSort;
        Collections.sort(buttonsToSort, (button1, button2) -> {
            int number1 = Integer.parseInt(button1.getName());
            int number2 = Integer.parseInt(button2.getName());
            return Integer.compare(number1, number2);
        });
        return buttons;
    }
    private List<MenuItem> getItemsByMenuBelonging(String belongsToMenu){
        return menuItemRepository.findAllByBelongsToMenu(belongsToMenu);
    }
    private List<Button> getButtons (String typeOfMenu){
        buttonRepository.findAllByBelongsToMenu(typeOfMenu);
        return buttonRepository.findAllByBelongsToMenu(typeOfMenu);
    }
    private List<Button> getConfirmationButtons (String typeOfMenu, String callbackDataWithId){
        String id = callbackDataWithId.substring(4);
        List<Button> buttons = buttonRepository.findAllByBelongsToMenu(typeOfMenu);
        for (Button button1 : buttons){
            if(button1.getCallbackData().equals("ADDTOCART")){
                button1.setCallbackData("ADDTOCART"+id);
            }
        }
        return buttons;
    }
    private String createConfirmationText(MenuItem item){
        return "Вы выбрали " + item.getName() +". Цена " +  item.getPrice();
    }
    private MenuItem findItemById(String id){
        return menuItemRepository.findById(Integer.valueOf(id.substring(4))).orElse(null);
    }
    private void addToCart(Long chatId, String callbackData){
        Integer menuItemId = Integer.valueOf(callbackData.substring(9));
        MenuItem menuItem = menuItemRepository.findById(menuItemId).orElse(null);
        if(cart.checkIfCartIsExpired()){
            cart.resetCart();
        }
        cart.setChatId(chatId);
        cart.setTimeOfCreation(LocalDateTime.now());
        cart.setTotalPrice(cart.getTotalPrice() + menuItem.getPrice());
        cart.getMenuItemList().add(menuItem);
    }
    private Order createOrder(Cart cart){
        Order order = new Order();
        order.setChatId(cart.getChatId().intValue());
        order.setTotalPrice(cart.getTotalPrice());
        List<MenuItem> items = cart.getMenuItemList();
        StringBuilder itemsAsString = new StringBuilder();
        for(MenuItem item : items){
            itemsAsString.append(item.getName()).append(" ");
        }
        order.setItems(itemsAsString.toString());
        orderRepository.save(order);
        return order;
    }
    private void deleteMenuItem(String messageText){
        String menuItemName = getItemsName(messageText);
        MenuItem menuItemForRemoval = menuItemRepository.findMenuItemByName(menuItemName);
        int menuItemId = menuItemForRemoval.getId();
        Button buttonForRemoval = buttonRepository.findButtonByCallbackData("ITEM"+menuItemId);
        buttonRepository.delete(buttonForRemoval);
        menuItemRepository.delete(menuItemForRemoval);
    }
    private void createMenuItem(String menuItemInfo){
        String itemInfo = menuItemInfo.trim();
        String menuItemName = getItemsName(itemInfo);
        int menuItemsPrice = Integer.valueOf(getItemsPrice(itemInfo));
        MenuItem menuItem = new MenuItem();
        menuItem.setName(menuItemName);
        menuItem.setPrice(menuItemsPrice);
        menuItem.setBelongsToMenu("unknown");
        menuItemRepository.save(menuItem);
    }

    private MenuItem setMenuItemsMenuBelonging(String belongsToMenu){
        MenuItem menuItem = menuItemRepository.findMenuItemByBelongsToMenu("unknown");
        menuItem.setBelongsToMenu(belongsToMenu.substring(4)+"menu");
        menuItemRepository.save(menuItem);
        return menuItem;
    }
    private void createButtonFromMenuItem(MenuItem menuItem){
        Button buttonToAdd = new Button();
        List<MenuItem> items = menuItemRepository.findAllByBelongsToMenu(menuItem.getBelongsToMenu());
        buttonToAdd.setName(String.valueOf(items.size()));
        buttonToAdd.setBelongsToMenu(menuItem.getBelongsToMenu());
        buttonToAdd.setCallbackData("ITEM"+String.valueOf(menuItem.getId()));
        buttonRepository.save(buttonToAdd);
    }
    private void removeFromCart(String callbackData){
        int index = Integer.valueOf(callbackData.substring(14));
        cart.getMenuItemList().remove(index);
    }



}
