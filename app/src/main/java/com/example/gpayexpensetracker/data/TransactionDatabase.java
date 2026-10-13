package com.example.gpayexpensetracker.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.Executors;

@Database(entities = {TransactionEntity.class}, version = 6, exportSchema = false)
public abstract class TransactionDatabase extends RoomDatabase {
    public abstract TransactionDao getTransactionDao();

    private static volatile TransactionDatabase INSTANCE = null;
    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN reason TEXT");
        }
    };

    public static TransactionDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (TransactionDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        TransactionDatabase.class,
                        "gpay_expense_tracker_db"
                    )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .addCallback(new RoomDatabase.Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                            Executors.newSingleThreadExecutor().execute(() -> {
                                TransactionDao dao = INSTANCE.getTransactionDao();
                                // Pre-populate ALL 227 real user CSV transactions (Zero fake data)
                                dao.insertTransaction(new TransactionEntity(1.0, "food", "Food", 1780415153000L, "Manual", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1.0, "kavisomuu@ok", "Others", 1780415430000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1.0, "shopping", "Shopping", 1780415986000L, "Manual", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1.0, "kavisomuu@ok", "Others", 1780417489000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(37.0, "DAVID J Onion 1 kg", "Food", 1780453562000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(647.82, "Udemy India linux", "Others", 1780456069000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(96.0, "metro", "Travel", 1780468303000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(600.0, "Vellore Inst transcript and provisional", "Others", 1780547053000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(443.0, "medicine", "Bills", 1780642920000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(160.0, "Distilled water", "Bills", 1780643651000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(20.0, "air for cycle wheel", "Others", 1780660073000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(82.0, "packing for appa office marriage", "Bills", 1780719321000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1589.0, "Flipkart Pay Home", "Bills", 1780728072000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(150.0, "turf", "Entertainment", 1780752916000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(70.0, "Rose Milk", "Food", 1780756863000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(527.1, "DOMINOS PIZZ", "Food", 1780762510000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(130.0, "print", "Others", 1780832697000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(3.0, "print", "Others", 1780836430000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(20.0, "two wheeler tire air", "Travel", 1780932564000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(39.0, "Milk half litre", "Bills", 1781055114000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(20.0, "aplam", "Food", 1781144681000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(32.0, "metro", "Others", 1781343617000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(128.0, "metro movie disclore day", "Others", 1781357521000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(60.0, "DEERENDRAKUM disclosure day movie", "Others", 1781346156000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(120.0, "Hari cut", "Others", 1781407721000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(120.0, "hair cut", "Bills", 1781408029000L, "Manual", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(345.0, "vegetable", "Food", 1781486290000L, "Manual", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(5.0, "chocolate", "Others", 1781520744000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(20.0, "mountain dew", "Food", 1781524535000L, "Manual", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(40.0, "bus", "Travel", 1781574743000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(120.0, "Atto", "Food", 1781529672000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(40.0, "milk", "Others", 1781618534000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(39.0, "milk", "Others", 1781656969000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(8.0, "graph", "Others", 1781707451000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(400.0, "petrol", "Others", 1781849662000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(120.0, "Snack", "Others", 1781953108000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(80.0, "lunch sat", "Others", 1781944379000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(180.0, "hospital", "Bills", 1782141742000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(137.0, "hospital", "Others", 1782144083000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(126.0, "hospital", "Bills", 1782144405000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(459.0, "petrol", "Travel", 1782183532000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(58.0, "noodles", "Food", 1782239912000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(120.0, "noodles", "Food", 1782311167000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(130.0, "lunch", "Food", 1782468656000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(39.9, "juice", "Food", 1782468694000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(476.0, "petrol", "Travel", 1782478207000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(110.0, "egg mullai and fried rice podi", "Shopping", 1782531157000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(72.0, "Battery", "Others", 1782653998000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(12.0, "print", "Bills", 1782745163000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(120.0, "snacks", "Food", 1782741127000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(503.0, "petrol", "Travel", 1782824955000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(15.0, "snacks", "Food", 1782806298000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(10.0, "air", "Travel", 1782999465000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(20.0, "putiyna", "Food", 1782959513000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(84.0, "vada", "Food", 1783132578000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(60.0, "Vishal cycle grip", "Others", 1783344154000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(3.0, "Fuse", "Others", 1783343321000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(90.0, "Bread Svs", "Food", 1783341346000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(50.0, "kann mye and comb", "Others", 1783341197000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(30.0, "bag zip", "Others", 1783341046000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(160.0, "onimemt", "Others", 1783340491000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(443.0, "aachi tata Medicenes", "Bills", 1783315040000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1110.0, "tbm kama things", "Shopping", 1783234340000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(123.0, "Noodles", "Food", 1783150155000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(100.0, "bhel puri", "Food", 1783164452000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(50.0, "gotumai", "Bills", 1783165113000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(168.0, "medical", "Bills", 1783139670000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(150.0, "temper", "Others", 1783515921000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(120.0, "hair cut", "Others", 1783571309000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(82.0, "Milk", "Others", 1783561146000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(192.0, "mango", "Others", 1783496512000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(456.67, "petrol", "Travel", 1783482721000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(48.0, "6 egg", "Food", 1783415825000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(165.0, "ironing shirts and pants", "Others", 1783603631000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(51.0, "rapido", "Travel", 1783917146000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(233.0, "Dinner", "Others", 1783865430000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(262.18, "Lunch", "Food", 1783838183000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1000.0, "Atm trial", "Bills", 1783711294000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(210.0, "night dinner", "Food", 1783954598000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(30.0, "morning share auto", "Travel", 1784001861000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(153.0, "dinner", "Food", 1784038394000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(30.0, "mrng auto", "Travel", 1784091385000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(30.0, "mrng tracel", "Travel", 1784179618000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(10.0, "trial", "Others", 1784175076000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(186.0, "night dinner", "Food", 1784124515000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(212.0, "dinner", "Others", 1784209818000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(30.0, "mrng auto", "Travel", 1784266302000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(30.0, "morning travel", "Travel", 1784526140000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(134.0, "Night dinner", "Food", 1784471375000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(57.0, "rapido", "Travel", 1784470619000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(3500.0, "advance for pg", "Bills", 1784466993000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(64.0, "rapido", "Travel", 1784433479000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(42.0, "rapido", "Travel", 1784432086000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(186.0, "9289592895-Indian Bank", "Food", 1784297176000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(210.0, "9289592895-Indian Bank", "Food", 1784359849000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(112.0, "9289592895-Indian Bank", "Food", 1784382295000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(145.0, "9289592895-Indian Bank", "Food", 1784382381000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(222.0, "dinner", "Food", 1784639348000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(765.0, "movie", "Entertainment", 1784638317000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1598.0, "dress", "Shopping", 1784634157000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(30.0, "travel mrng", "Travel", 1784612876000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(2938.75, "shoe", "Shopping", 1784546534000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(68.0, "rapido", "Travel", 1784544858000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(50.0, "rapido", "Travel", 1784548042000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(226.0, "dinner", "Food", 1784553455000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(75.0, "rapido", "Travel", 1784599580000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(30.0, "mrng auto", "Travel", 1784698113000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(105.0, "snacks", "Others", 1784719293000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(741.85, "dinner", "Food", 1784724646000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(30.0, "mrng travel", "Travel", 1784785173000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(163.0, "snacks", "Food", 1784808154000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(266.43, "dinner", "Food", 1784815575000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(3541.0, "Laundry", "Bills", 1784860647000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(37.0, "rapido", "Travel", 1784961254000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1313.55, "basics for pg", "Bills", 1784960265000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(67.0, "rapido", "Travel", 1784959182000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(390.0, "mountain dew", "Food", 1784948702000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(51.0, "rapido", "Travel", 1784943925000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(632.48, "the oddessy movie", "Entertainment", 1784911478000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(10.0, "News paper", "Bills", 1784900867000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(130.0, "rapido", "Travel", 1784871996000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(68.0, "rapido", "Travel", 1785058528000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(79.0, "rapido", "Travel", 1785080690000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(179.0, "Hot wheels", "Entertainment", 1785139599000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(29.0, "Dinner", "Food", 1785159391000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1993.1, "cahoot dress", "Shopping", 1785383815000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(179.0, "hot wheels", "Entertainment", 1785416083000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(4.0, "9289592895-Indian Bank", "Food", 1785419083000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(61.0, "hot wheels", "Entertainment", 1785550589000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1749.0, "bus to hone", "Travel", 1785600911000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(185.0, "dinner", "Food", 1785593906000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(82.0, "rapido to madhin", "Travel", 1785644940000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(155.0, "rapido", "Travel", 1785663436000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(80.0, "rapido", "Travel", 1785679059000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(105.0, "rapido", "Travel", 1785854264000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(270.0, "Sweet", "Others", 1785853058000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1093.0, "sweet", "Others", 1785852984000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(108.0, "rapido", "Travel", 1785846732000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(390.0, "biscuit", "Others", 1785843627000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1783.0, "trimmer", "Shopping", 1785827722000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(3.0, "dinner", "Food", 1785765091000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(176.0, "stuff", "Shopping", 1786026780000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(3500.0, "badminton bat lining ignite 8", "Entertainment", 1786025578000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(921.0, "tata medicines", "Bills", 1785921590000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(170.0, "rapido", "Travel", 1786422663000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(711.0, "minimalist", "Shopping", 1786369238000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(750.0, "badminton net", "Entertainment", 1786276413000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(185.0, "rapido", "Travel", 1785931648000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(158.0, "rapido", "Travel", 1785937037000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1664.5, "gta 5", "Entertainment", 1785997300000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(150.0, "badminton", "Entertainment", 1786077934000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1492.0, "to Hyderabad", "Travel", 1786159704000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(889.0, "tata eye medicine", "Bills", 1786082561000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(180.0, "rapido", "Travel", 1786084337000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(150.0, "rapido", "Travel", 1786333005000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(3400.0, "suitcase", "Shopping", 1786440661000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(32.0, "metro", "Travel", 1786450703000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(160.0, "snacks", "Food", 1786451282000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(200.0, "invest", "Bills", 1786507631000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(45.0, "rapido", "Travel", 1786521081000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(161.35, "rapido", "Travel", 1786508824000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(69.0, "metro", "Travel", 1786501862000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(232.0, "volini", "Bills", 1786539412000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(30.0, "banana", "Food", 1786543835000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(45.0, "rapido", "Travel", 1786576980000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(45.0, "rapido", "Travel", 1786583158000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(45.0, "rapido", "Travel", 1786750380000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(122.0, "badminton", "Entertainment", 1786755160000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(53.0, "rapido", "Travel", 1786758708000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(20.0, "sprite", "Food", 1786797745000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(164.58, "mefil briyani not so good don't buy again until you want to eat white rice", "Food", 1786801917000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1062.54, "rock climbing", "Entertainment", 1787033535000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(300.0, "kite", "Bills", 1786960078000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(125.0, "badminton", "Entertainment", 1786927460000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(45.0, "rapido", "Travel", 1786922590000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(142.93, "noodles", "Food", 1786874964000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(4534.12, "Bus", "Travel", 1787072077000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(117.0, "french fries", "Food", 1787099943000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(145.72, "noodles", "Food", 1787109407000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(66.0, "rapido", "Travel", 1787222237000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(175.23, "fried rice", "Food", 1787224049000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(20.0, "banana", "Food", 1787241048000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(133.0, "rapido", "Travel", 1787285846000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(528.56, "petrol destiny", "Travel", 1787387365000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(100.0, "investment", "Bills", 1788330544000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(100.0, "investment", "Bills", 1788330524000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(8500.0, "eent", "Bills", 1788317188000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(120.48, "kara dosa and idili", "Food", 1788317246000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(118.0, "badminton", "Entertainment", 1788309261000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(500.0, "investment", "Bills", 1788283264000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(500.0, "investment", "Bills", 1788283249000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(3409.0, "decathlon shoes bag trouser socks", "Shopping", 1788181364000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(10.0, "bananaa", "Food", 1788189562000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(95.0, "badminton", "Entertainment", 1788395648000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(45.0, "Maggie", "Food", 1788407166000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(161.13, "Dinner", "Food", 1788447754000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(27.0, "egg and sprite", "Food", 1788701469000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(526.0, "soap and shampoo", "Shopping", 1788700576000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1768.4, "jeans", "Shopping", 1788694909000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(259.0, "movies", "Entertainment", 1788619691000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(226.17, "badminton", "Others", 1788683075000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(38.0, "rapido", "Travel", 1788828930000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(118.0, "badminton", "Entertainment", 1788827605000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(141.59, "breakfast", "Food", 1788921668000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(14.0, "dinner", "Food", 1788876265000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(230.0, "rapido", "Travel", 1789059400000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1.0, "dinner", "Food", 1789047133000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(181.0, "rapido", "Travel", 1789491849000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(240.0, "rapido", "Travel", 1789411428000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(3265.0, "trip", "Entertainment", 1789411163000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(326.0, "ice cream", "Food", 1789980561000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(153.0, "badminton", "Entertainment", 1789974012000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(50.0, "rapido", "Travel", 1789924252000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(230.0, "dinner", "Food", 1789921402000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(280.0, "lunch", "Food", 1789916576000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(50.0, "chocolate", "Food", 1790178627000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(11.0, "cake", "Food", 1790160165000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(184.0, "Hotwheels", "Entertainment", 1790647776000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(2390.0, "shirts flipkart", "Shopping", 1790647027000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(4850.0, "trip", "Entertainment", 1790645730000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(80.0, "Noodles", "Food", 1790521406000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(50.0, "Chocolate", "Food", 1790507322000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1368.0, "shirts flipkart", "Shopping", 1790394525000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(2648.0, "souled store dress", "Shopping", 1790689055000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(628.0, "potluck", "Food", 1790692670000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(1200.0, "investment", "Bills", 1790770672000L, "SMS", "CSV Import", false));
                                dao.insertTransaction(new TransactionEntity(700.0, "investment", "Bills", 1790770477000L, "SMS", "CSV Import", false));
                            });
                        }
                    })
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
