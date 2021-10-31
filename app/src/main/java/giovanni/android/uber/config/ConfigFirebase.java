package giovanni.android.uber.config;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class ConfigFirebase {

    private static DatabaseReference database;
    private static FirebaseAuth auth;

    //retorna instância do FirebaseDatabase
    public static DatabaseReference getFirebaseDatabase(){
        if (database == null){
            database = FirebaseDatabase.getInstance().getReference();
        }
        return database;
    }

    //retorna instância do FirebaseAuth
    public static FirebaseAuth getFirebaseAuth(){
        if (auth == null){
            auth = FirebaseAuth.getInstance();
        }
        return auth;
    }

}
