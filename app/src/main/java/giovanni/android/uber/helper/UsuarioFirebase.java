package giovanni.android.uber.helper;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import giovanni.android.uber.activity.PassageiroActivity;
import giovanni.android.uber.activity.RequisicoesActivity;
import giovanni.android.uber.config.ConfigFirebase;
import giovanni.android.uber.model.Usuario;

public class UsuarioFirebase {

    public static FirebaseUser getUsuarioAtual(){
        FirebaseAuth usuario = ConfigFirebase.getFirebaseAuth();
        return usuario.getCurrentUser();
    }

    public static Usuario getDadosUsuarioLogado(){

        FirebaseUser firebaseUser = getUsuarioAtual();

        Usuario usuario = new Usuario();
        usuario.setId(firebaseUser.getUid());
        usuario.setEmail(firebaseUser.getEmail());
        usuario.setNome(firebaseUser.getDisplayName());

        return usuario;

    }

    public static boolean atualizarNomeUsuario(String nome){

        try {

            FirebaseUser user = getUsuarioAtual();
            UserProfileChangeRequest profile = new UserProfileChangeRequest.Builder()
                    .setDisplayName(nome)
                    .build();
            user.updateProfile(profile).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (!task.isSuccessful()) {
                        Log.d("Perfil", "Erro ao atualizar o nome de perfil");
                    }
                }
            });

            return true;

        }catch (Exception e){
            e.printStackTrace();
            return false;
        }

    }

    public static void redirecionaUsuarioLogado(final Activity activity){

        FirebaseUser user = getUsuarioAtual();
        if (user != null) {

            DatabaseReference usuariosRef = ConfigFirebase.getFirebaseDatabase()
                    .child("usuarios")
                    .child(getIDUsuario());
            usuariosRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                    Usuario usuario = dataSnapshot.getValue(Usuario.class);

                    String tipoUsuario = usuario.getTipo();
                    if (tipoUsuario.equals("M")) {
                        activity.startActivity(new Intent(activity, RequisicoesActivity.class));
                    }else {
                        activity.startActivity(new Intent(activity, PassageiroActivity.class));
                    }

                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });

        }
    }

    public static void atualizarDadosLocalizacao(double lat, double lon){

        //Define nó de local de usuário
        DatabaseReference localUsuario = ConfigFirebase.getFirebaseDatabase()
                .child("local_usuario");
        GeoFire geoFire = new GeoFire(localUsuario);

        //Recupera dados usuário logado
        Usuario usuarioLogado = UsuarioFirebase.getDadosUsuarioLogado();

        //Configura localização do usuário
        geoFire.setLocation(usuarioLogado.getId(),
                new GeoLocation(lat, lon),
                new GeoFire.CompletionListener() {
                    @Override
                    public void onComplete(String key, DatabaseError error) {
                        if (error != null) {
                            Log.d("erro", "Erro ao salvar local!");
                        }
                    }
                });

    }

    public static String getIDUsuario(){
        return getUsuarioAtual().getUid();
    }

}
