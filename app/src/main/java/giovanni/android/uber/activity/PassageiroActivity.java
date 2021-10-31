package giovanni.android.uber.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import giovanni.android.uber.R;
import giovanni.android.uber.config.ConfigFirebase;
import giovanni.android.uber.helper.Local;
import giovanni.android.uber.helper.UsuarioFirebase;
import giovanni.android.uber.model.Destino;
import giovanni.android.uber.model.Requisicao;
import giovanni.android.uber.model.Usuario;

public class PassageiroActivity extends AppCompatActivity implements OnMapReadyCallback {

    /*
     * Lat/lon destino: -23.556407, -46.662365
     * Lat/lon passageiro: -23.562791, -46.654668
     * Lat/lon motorista (a caminho):
     *  inicial: -23.563196, -46.65607
     *  intermediária: -23.564801, -46.652196
     *  final: -23.563136, -46.654247
     */


    //Componentes
    private EditText editDestino;
    private LinearLayout linearLayoutDestino;
    private Button buttonChamarUber;

    private GoogleMap mMap;
    private FirebaseAuth auth;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private LatLng localPassageiro;
    private boolean cancelarUber = false;
    private DatabaseReference firebaseRef;
    private Requisicao requisicao;
    private Usuario passageiro;
    private String statusRequisicao;
    private Destino destino;
    private Marker marcadorMotorista;
    private Marker marcadorPassageiro;
    private Marker marcadorDestino;
    private Usuario motorista;
    private LatLng localMotorista;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passageiro);

        inicializarComponentes();

        //Adiciona listener para o status da requisição
        verificaStatusRequisicao();

    }

    private void verificaStatusRequisicao(){

        Usuario usuarioLogado = UsuarioFirebase.getDadosUsuarioLogado();
        DatabaseReference requisicoes = firebaseRef.child("requisicoes");
        Query requisicaoPesquisa = requisicoes.orderByChild("passageiro/id")
                .equalTo(usuarioLogado.getId());

        requisicaoPesquisa.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                List<Requisicao> lista = new ArrayList<>();
                for (DataSnapshot ds: dataSnapshot.getChildren()){
                    lista.add(ds.getValue(Requisicao.class));
                }

                Collections.reverse(lista);
                if (lista!=null && lista.size()>0) {
                    requisicao = lista.get(0);

                    if (requisicao != null) {
                        if (!requisicao.getStatus().equals(Requisicao.STATUS_ENCERRADA)) {
                            passageiro = requisicao.getPassageiro();
                            localPassageiro = new LatLng(
                                    Double.parseDouble(passageiro.getLatitude()),
                                    Double.parseDouble(passageiro.getLongitude())
                            );
                            statusRequisicao = requisicao.getStatus();
                            destino = requisicao.getDestino();
                            if (requisicao.getMotorista() != null){
                                motorista = requisicao.getMotorista();
                                localMotorista = new LatLng(
                                        Double.parseDouble(motorista.getLatitude()),
                                        Double.parseDouble(motorista.getLongitude())
                                );
                            }
                            alteraInterfaceStatusRequisicao(statusRequisicao);
                        }
                    }
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void alteraInterfaceStatusRequisicao(String status){

        if (status != null && !status.isEmpty()) {
            cancelarUber = false;
            switch (status){
                case Requisicao.STATUS_AGUARDANDO :
                    requisicaoAguardando();
                    break;
                case Requisicao.STATUS_A_CAMINHO :
                    requisicaoACaminho();
                    break;
                case Requisicao.STATUS_VIAGEM :
                    requisicaoViagem();
                    break;
                case Requisicao.STATUS_FINALIZADA :
                    requisicaoFinalizada();
                    break;
                case Requisicao.STATUS_CANCELADA :
                    requisicaoCancelada();
                    break;
            }
        }else {
            //Adiciona marcador passageiro
            adicionaMarcadorPassageiro(localPassageiro, "Seu local");
            centralizarMarcador(localPassageiro);
        }

    }

    private void requisicaoCancelada(){

        linearLayoutDestino.setVisibility(View.VISIBLE);
        buttonChamarUber.setText("Chamar Uber");
        cancelarUber = false;

    }

    private void requisicaoAguardando(){

        linearLayoutDestino.setVisibility(View.GONE);
        buttonChamarUber.setText("Cancelar Uber");
        cancelarUber = true;

        adicionaMarcadorPassageiro(localPassageiro, passageiro.getNome());
        centralizarMarcador(localPassageiro);

    }

    private void requisicaoACaminho(){

        linearLayoutDestino.setVisibility(View.GONE);
        buttonChamarUber.setText("Motorista a caminho");
        buttonChamarUber.setEnabled(false);

        //Adiciona marcador passageiro
        adicionaMarcadorPassageiro(localPassageiro, passageiro.getNome());
        //Adiciona marcador motorista
        adicionaMarcadorMotorista(localMotorista, motorista.getNome());
        //Centralizar passageiro/motorista
        centralizarDoisMarcadores(marcadorMotorista, marcadorPassageiro);
    }

    private void requisicaoViagem(){

        linearLayoutDestino.setVisibility(View.GONE);
        buttonChamarUber.setText("A caminho do destino");
        buttonChamarUber.setEnabled(false);

        //Adiciona marcador motorista
        adicionaMarcadorMotorista(localMotorista, motorista.getNome());

        //Adiciona marcador de destino
        LatLng localDestino = new LatLng(
                Double.parseDouble(destino.getLatitude()),
                Double.parseDouble(destino.getLongitude())
        );
        adicionaMarcadorDestino(localDestino, "Destino");

        //Centraliza marcadores motorista/destino
        centralizarDoisMarcadores(marcadorMotorista, marcadorDestino);

    }

    private void requisicaoFinalizada(){

        linearLayoutDestino.setVisibility(View.GONE);
        buttonChamarUber.setEnabled(false);

        //Adiciona marcador de destino
        LatLng localDestino = new LatLng(
                Double.parseDouble(destino.getLatitude()),
                Double.parseDouble(destino.getLongitude())
        );
        adicionaMarcadorDestino(localDestino, "Destino");
        centralizarMarcador(localDestino);

        //Calcular distância
        float distancia = Local.calcularDistancia(localPassageiro, localDestino);
        float valor = distancia*4;
        DecimalFormat decimal = new DecimalFormat("0.00");
        String resultado = decimal.format(valor);

        buttonChamarUber.setText("Corrida Finalizada - R$ " + resultado);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Total da viagem")
                .setMessage("Sua viagem ficou: R$"+resultado)
                .setCancelable(false)
                .setNegativeButton("Encerrar viagem", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        requisicao.setStatus(Requisicao.STATUS_ENCERRADA);
                        requisicao.atualizarStatus();

                        finish();
                        startActivity(new Intent(getIntent()));

                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();

    }

    private void adicionaMarcadorPassageiro(LatLng localizacao, String titulo){

        if (marcadorPassageiro != null){
            marcadorPassageiro.remove();
        }

        marcadorPassageiro = mMap.addMarker(
                new MarkerOptions()
                        .position(localizacao)
                        .title(titulo)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.usuario))
        );
    }

    private void adicionaMarcadorMotorista(LatLng localizacao, String titulo){

        if (marcadorMotorista != null){
            marcadorMotorista.remove();
        }

        marcadorMotorista = mMap.addMarker(
                new MarkerOptions()
                        .position(localizacao)
                        .title(titulo)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.carro))
        );

    }

    private void adicionaMarcadorDestino(LatLng localizacao, String titulo){

        if (marcadorPassageiro != null){
            marcadorPassageiro.remove();
        }

        if (marcadorDestino != null){
            marcadorDestino.remove();
        }

        marcadorDestino = mMap.addMarker(
                new MarkerOptions()
                        .position(localizacao)
                        .title(titulo)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.destino))
        );

    }

    private void centralizarMarcador(LatLng local){
        mMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(local, 20)
        );
    }

    private void centralizarDoisMarcadores(Marker m1, Marker m2){

        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        builder.include(m1.getPosition());
        builder.include(m2.getPosition());

        LatLngBounds bounds = builder.build();

        int largura = getResources().getDisplayMetrics().widthPixels;
        int altura = getResources().getDisplayMetrics().heightPixels;
        int espacoInterno = (int) (largura*0.20);

        mMap.moveCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, largura, altura, espacoInterno)
        );

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        //recuperar localização do usuário
        recuperarLozalizacaoUsuario();

    }

    private void recuperarLozalizacaoUsuario() {

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {

                //recuperar latitude e longitude
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                localPassageiro = new LatLng(latitude, longitude);

                //Atualizar GeoFire
                UsuarioFirebase.atualizarDadosLocalizacao(latitude, longitude);

                //Altera interface de acordo com o status
                alteraInterfaceStatusRequisicao(statusRequisicao);

                if (statusRequisicao != null && !statusRequisicao.isEmpty()) {
                    if (statusRequisicao.equals(Requisicao.STATUS_VIAGEM) || statusRequisicao.equals(Requisicao.STATUS_FINALIZADA)) {
                        locationManager.removeUpdates(locationListener);
                    }else {
                        //Solicitar atualizações de localização
                        if (ActivityCompat.checkSelfPermission(PassageiroActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            locationManager.requestLocationUpdates(
                                    LocationManager.GPS_PROVIDER,
                                    10000,
                                    10,
                                    locationListener
                            );
                        }
                    }
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        //Solicitar atualizações de localização
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10000,
                    10,
                    locationListener
            );
        }

    }

    public void chamarUber(View view){

        if (cancelarUber) {

            //cancelar a requisição
            requisicao.setStatus(Requisicao.STATUS_CANCELADA);
            requisicao.atualizarStatus();

        }else {

            //uber não foi chamado

            String enderecoDestino = editDestino.getText().toString();

            if (!enderecoDestino.equals("") || enderecoDestino != null){

                Address addressDestino = recuperarEndereco(enderecoDestino);
                if (addressDestino != null) {

                    final Destino destino = new Destino();
                    destino.setCidade(addressDestino.getAdminArea());
                    destino.setCep(addressDestino.getPostalCode());
                    destino.setBairro(addressDestino.getSubLocality());
                    destino.setRua(addressDestino.getThoroughfare());
                    destino.setNumero(addressDestino.getFeatureName());
                    destino.setLatitude(String.valueOf(addressDestino.getLatitude()));
                    destino.setLongitude(String.valueOf(addressDestino.getLongitude()));

                    StringBuilder mensagem = new StringBuilder();
                    mensagem.append("Cidade: " + destino.getCidade());
                    mensagem.append("\nRua: " + destino.getRua());
                    mensagem.append("\nBairro: " + destino.getBairro());
                    mensagem.append("\nNúmero: " + destino.getNumero());
                    mensagem.append("\nCEP: " + destino.getCep());

                    AlertDialog.Builder builder = new AlertDialog.Builder(this)
                            .setTitle("Confirme seu endereço!")
                            .setMessage(mensagem)
                            .setPositiveButton("Confirmar", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                    salvarRequisicao(destino);

                                }
                            }).setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                }
                            });
                    AlertDialog dialog = builder.create();
                    dialog.show();

                }

            }else {
                Toast.makeText(this, "Informe o endereço de destino!", Toast.LENGTH_SHORT).show();
            } //Fim

        }



    }

    private void salvarRequisicao(Destino destino){

        Requisicao requisicao = new Requisicao();
        requisicao.setDestino(destino);

        Usuario usuarioPassageiro = UsuarioFirebase.getDadosUsuarioLogado();
        usuarioPassageiro.setLatitude(String.valueOf(localPassageiro.latitude));
        usuarioPassageiro.setLongitude(String.valueOf(localPassageiro.longitude));

        requisicao.setPassageiro(usuarioPassageiro);
        requisicao.setStatus(Requisicao.STATUS_AGUARDANDO);
        requisicao.salvar();

        linearLayoutDestino.setVisibility(View.GONE);
        buttonChamarUber.setText("Cancelar Uber");

    }

    private Address recuperarEndereco(String endereco){

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> listaEnderecos = geocoder.getFromLocationName(endereco, 1);
            if (listaEnderecos != null && listaEnderecos.size() > 0){
                Address address = listaEnderecos.get(0);

                return address;

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        switch (item.getItemId()){
            case R.id.menuSair :
                auth.signOut();
                finish();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void inicializarComponentes(){

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Iniciar uma viagem");
        setSupportActionBar(toolbar);

        //Configurações iniciais
        firebaseRef = ConfigFirebase.getFirebaseDatabase();

        //Inicializar componentes
        editDestino = findViewById(R.id.editDestino);
        linearLayoutDestino = findViewById(R.id.linearLayoutDestino);
        buttonChamarUber = findViewById(R.id.buttonChamarUber);

        auth = ConfigFirebase.getFirebaseAuth();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }
}
