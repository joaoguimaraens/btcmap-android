package com.bubelov.coins.ui.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bubelov.coins.Constants;
import com.bubelov.coins.repository.area.NotificationAreaRepository;
import com.bubelov.coins.repository.notification.PlaceNotificationsRepository;
import com.bubelov.coins.repository.place.PlacesRepository;
import com.bubelov.coins.repository.placecategory.PlaceCategoriesRepository;
import com.bubelov.coins.repository.user.UserRepository;
import com.bubelov.coins.R;
import com.bubelov.coins.model.Place;
import com.bubelov.coins.model.NotificationArea;
import com.bubelov.coins.model.User;
import com.bubelov.coins.database.sync.DatabaseSync;
import com.bubelov.coins.database.sync.DatabaseSyncService;
import com.bubelov.coins.ui.widget.PlaceDetailsView;
import com.bubelov.coins.util.Analytics;
import com.bubelov.coins.repository.placecategory.marker.PlaceCategoriesMarkersRepository;
import com.bubelov.coins.util.OnCameraChangeMultiplexer;
import com.bubelov.coins.util.StaticClusterRenderer;
import com.bubelov.coins.util.IntentUtils;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * @author Igor Bubelov
 */

public class MapActivity extends AbstractActivity implements OnMapReadyCallback, Toolbar.OnMenuItemClickListener, DatabaseSync.Callback {
    private static final String PLACE_ID_EXTRA = "place_id";
    private static final String CLEAR_PLACE_NOTIFICATIONS_EXTRA = "clear_place_notifications";

    private static final int REQUEST_CHECK_LOCATION_SETTINGS = 10;
    private static final int REQUEST_ACCESS_LOCATION = 20;
    private static final int REQUEST_FIND_PLACE = 30;
    private static final int REQUEST_ADD_PLACE = 40;
    private static final int REQUEST_EDIT_PLACE = 50;
    private static final int REQUEST_SIGN_IN = 60;
    private static final int REQUEST_PROFILE = 70;

    private static final float MAP_DEFAULT_ZOOM = 13;

    @BindView(R.id.drawer_layout)
    DrawerLayout drawerLayout;

    @BindView(R.id.navigation_view)
    NavigationView navigationView;

    View drawerHeader;

    ImageView avatar;

    TextView userName;

    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @BindView(R.id.fab)
    View actionButton;

    @BindView(R.id.place_details)
    PlaceDetailsView placeDetails;

    @Inject
    UserRepository userRepository;

    @Inject
    NotificationAreaRepository notificationAreaRepository;

    @Inject
    PlacesRepository placesRepository;

    @Inject
    PlaceNotificationsRepository placeNotificationsRepository;

    @Inject
    PlaceCategoriesRepository placeCategoriesRepository;

    @Inject
    PlaceCategoriesMarkersRepository placeCategoriesMarkersRepository;

    private ActionBarDrawerToggle drawerToggle;

    private GoogleMap map;

    private ClusterManager<PlaceMarker> placesManager;

    private Place selectedPlace;

    private GoogleApiClient googleApiClient;

    private boolean firstLaunch;

    private BottomSheetBehavior bottomSheetBehavior;

    @Inject
    DatabaseSync databaseSync;

    private Snackbar initialSyncSnackbar;

    public static Intent newIntent(Context context, long placeId) {
        Intent intent = new Intent(context, MapActivity.class);

        if (placeId != 0) {
            intent.putExtra(PLACE_ID_EXTRA, placeId);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(CLEAR_PLACE_NOTIFICATIONS_EXTRA, true);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dependencies().inject(this);
        setContentView(R.layout.activity_map);
        ButterKnife.bind(this);

        drawerHeader = navigationView.getHeaderView(0);
        avatar = ButterKnife.findById(drawerHeader, R.id.avatar);
        userName = ButterKnife.findById(drawerHeader, R.id.user_name);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addApi(Auth.GOOGLE_SIGN_IN_API)
                .addConnectionCallbacks(new LocationApiConnectionCallbacks())
                .enableAutoManage(this, connectionResult -> Toast.makeText(MapActivity.this, connectionResult.getErrorMessage(), Toast.LENGTH_SHORT).show())
                .build();

        googleApiClient.connect();

        firstLaunch = savedInstanceState == null;

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        bottomSheetBehavior = BottomSheetBehavior.from(placeDetails);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                placeDetails.setFullScreen(newState == BottomSheetBehavior.STATE_EXPANDED);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                actionButton.setVisibility(slideOffset > 0.5f ? View.GONE : View.VISIBLE);
            }
        });

        new Handler(Looper.getMainLooper()).postDelayed(() -> bottomSheetBehavior.setPeekHeight(placeDetails.getHeaderHeight()), 1000);

        placeDetails.setCallback(new PlaceDetailsView.Callback() {
            @Override
            public void onEditPlaceClick(@NonNull Place place) {
                if (!TextUtils.isEmpty(userRepository.getUserAuthToken())) {
                    EditPlaceActivity.Companion.startForResult(MapActivity.this, place.getId(), null, REQUEST_EDIT_PLACE);
                } else {
                    signIn();
                }
            }

            @Override
            public void onDismissed() {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(navigationView));
        toolbar.inflateMenu(R.menu.map);
        toolbar.setOnMenuItemClickListener(this);

        navigationView.setNavigationItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.action_exchange_rates:
                    drawerLayout.closeDrawers();
                    openExchangeRatesScreen();
                    return true;

                case R.id.action_notification_area:
                    drawerLayout.closeDrawers();
                    openNotificationAreaScreen();
                    return true;

                case R.id.action_chat:
                    drawerLayout.closeDrawers();
                    openChat();
                    return true;

                case R.id.action_settings:
                    drawerLayout.closeDrawers();
                    openSettingsScreen();
                    return true;
            }

            return false;
        });

        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close);
        drawerLayout.addDrawerListener(drawerToggle);

        updateDrawerHeader();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    protected void onStart() {
        super.onStart();

        initialSyncSnackbar = Snackbar.make(findViewById(R.id.coordinator_layout), "Loading places", Snackbar.LENGTH_INDEFINITE);

        databaseSync.addCallback(this);

        if (placesRepository.getCachedPlacesCount() == 0) {
            DatabaseSyncService.Companion.start(this);
            initialSyncSnackbar.show();
        }
    }

    @Override
    protected void onStop() {
        databaseSync.removeCallback(this);
        initialSyncSnackbar.dismiss();
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CHECK_LOCATION_SETTINGS && resultCode == RESULT_OK) {
            moveToLastLocation();
        }

        if (requestCode == REQUEST_FIND_PLACE && resultCode == RESULT_OK) {
            selectPlace(data.getLongExtra(PlacesSearchActivity.PLACE_ID_EXTRA, -1));
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(selectedPlace.getLatitude(), selectedPlace.getLongitude()), MAP_DEFAULT_ZOOM));
        }

        if (requestCode == REQUEST_ADD_PLACE && resultCode == RESULT_OK) {
            refreshMap();
        }

        if (requestCode == REQUEST_EDIT_PLACE && resultCode == RESULT_OK) {
            refreshMap();

            if (selectedPlace != null) {
                selectPlace(selectedPlace.getId());
            }
        }

        if (requestCode == REQUEST_SIGN_IN && resultCode == RESULT_OK) {
            updateDrawerHeader();
        }

        if (requestCode == REQUEST_PROFILE && resultCode == ProfileActivity.RESULT_SIGN_OUT) {
            updateDrawerHeader();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_add:
                if (!TextUtils.isEmpty(userRepository.getUserAuthToken())) {
                    EditPlaceActivity.Companion.startForResult(this, 0, map.getCameraPosition(), REQUEST_ADD_PLACE);
                } else {
                    signIn();
                }

                return true;
            case R.id.action_search:
                Location lastLocation = null;

                if (googleApiClient != null && googleApiClient.isConnected() && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
                }

                PlacesSearchActivity.Companion.startForResult(this, lastLocation, REQUEST_FIND_PLACE);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_ACCESS_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }

                    map.setMyLocationEnabled(true);
                    moveToLastLocation();
                }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(navigationView)) {
            drawerLayout.closeDrawer(navigationView);
            return;
        }

        switch (bottomSheetBehavior.getState()) {
            case BottomSheetBehavior.STATE_EXPANDED:
            case BottomSheetBehavior.STATE_SETTLING:
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                break;
            case BottomSheetBehavior.STATE_COLLAPSED:
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                break;
            case BottomSheetBehavior.STATE_HIDDEN:
                super.onBackPressed();
                break;
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setCompassEnabled(false);
        map.getUiSettings().setMapToolbarEnabled(false);

        initClustering();

        map.setOnCameraChangeListener(new OnCameraChangeMultiplexer(placesManager, new CameraChangeListener()));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.setMyLocationEnabled(true);
            moveToLastLocation();
        } else {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                moveToDefaultLocation();

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_ACCESS_LOCATION);
            } else {
                moveToDefaultLocation();
            }
        }

        handleIntent(getIntent());
    }

    @Override
    public void onDatabaseSyncFinished() {
        runOnUiThread(() -> {
            initialSyncSnackbar.dismiss();
            refreshMap();
        });
    }

    @Override
    public void onDatabaseSyncError() {
        runOnUiThread(() -> initialSyncSnackbar.dismiss());
    }

    private void updateDrawerHeader() {
        if (!TextUtils.isEmpty(userRepository.getUserAuthToken())) {
            User user = userRepository.getUser();

            if (!TextUtils.isEmpty(user.getAvatarUrl())) {
                Picasso.with(this).load(user.getAvatarUrl()).into(avatar);
            } else {
                avatar.setImageResource(R.drawable.ic_no_avatar);
            }

            if (!TextUtils.isEmpty(user.getFirstName())) {
                userName.setText(String.format("%s %s", user.getFirstName(), user.getLastName()));
            } else {
                userName.setText(user.getEmail());
            }
        } else {
            avatar.setImageResource(R.drawable.ic_no_avatar);
            userName.setText(R.string.guest);
        }

        drawerHeader.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(userRepository.getUserAuthToken())) {
                startActivityForResult(ProfileActivity.newIntent(this),  REQUEST_PROFILE, ActivityOptionsCompat.makeBasic().toBundle());
            } else {
                signIn();
            }

            drawerLayout.closeDrawers();
        });
    }

    private void handleIntent(final Intent intent) {
        if (intent.getBooleanExtra(CLEAR_PLACE_NOTIFICATIONS_EXTRA, false)) {
            placeNotificationsRepository.clear();
        }

        if (intent.hasExtra(PLACE_ID_EXTRA)) {
            selectPlace(intent.getLongExtra(PLACE_ID_EXTRA, -1));

            if (selectedPlace != null && map != null) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(selectedPlace.getLatitude(), selectedPlace.getLongitude()), MAP_DEFAULT_ZOOM));
            }
        }
    }

    private void signIn() {
        Intent intent = SignInActivity.newIntent(this);
        startActivityForResult(intent, REQUEST_SIGN_IN);
    }

    private void openExchangeRatesScreen() {
        Intent intent = new Intent(MapActivity.this, ExchangeRatesActivity.class);
        startActivity(intent);
        Analytics.logSelectContentEvent("exchange_rates", null, "screen");
    }

    private void openNotificationAreaScreen() {
        if (map == null) {
            return;
        }

        Intent intent = NotificationAreaActivity.Companion.newIntent(this, map.getCameraPosition());
        startActivity(intent);
        Analytics.logSelectContentEvent("notification_area", null, "screen");
    }

    private void openChat() {
        IntentUtils.INSTANCE.openUrl(this, "https://t.me/joinchat/AAAAAAwVT4aVBdFzcKKbsw");
        Analytics.logSelectContentEvent("chat", null, "screen");
    }

    private void openSettingsScreen() {
        SettingsActivity.start(this);
    }

    private void moveToLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);

        if (lastLocation != null) {
            onUserLocationReceived(lastLocation);
        } else {
            moveToDefaultLocation();
        }
    }

    private void moveToDefaultLocation() {
        LatLng defaultLocation = new LatLng(Constants.SAN_FRANCISCO_LATITUDE, Constants.SAN_FRANCISCO_LONGITUDE);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, MAP_DEFAULT_ZOOM));
    }

    private void onUserLocationReceived(Location location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, MAP_DEFAULT_ZOOM));

        if (notificationAreaRepository.getNotificationArea() == null) {
            NotificationArea area = new NotificationArea(
                    location.getLatitude(),
                    location.getLongitude(),
                    Constants.DEFAULT_NOTIFICATION_AREA_RADIUS_METERS
            );

            notificationAreaRepository.setNotificationArea(area);
        }
    }

    private void initClustering() {
        placesManager = new ClusterManager<>(this, map);
        PlacesRenderer renderer = new PlacesRenderer(this, map, placesManager);
        placesManager.setRenderer(renderer);
        renderer.setOnClusterItemClickListener(new ClusterItemClickListener());

        map.setOnCameraChangeListener(placesManager);
        map.setOnMarkerClickListener(placesManager);
        map.setOnMapClickListener(latLng -> {
            selectedPlace = null;
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        });
    }

    private void refreshMap() {
        if (map == null) {
            return;
        }

        LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
        Collection<Place> places = placesRepository.getPlaces(bounds);

        placesManager.clearItems();
        placesManager.addItems(toPlaceMarkers(places));
        placesManager.cluster();
    }

    private Collection<PlaceMarker> toPlaceMarkers(Collection<Place> places) {
        Collection<PlaceMarker> placeMarkers = new ArrayList<>();

        for (Place place : places) {
            placeMarkers.add(new PlaceMarker(place.getId(), place.getLatitude(), place.getLongitude()));
        }

        return placeMarkers;
    }

    private void selectPlace(long placeId) {
        selectedPlace = placesRepository.getPlace(placeId);

        if (selectedPlace == null) {
            return;
        }

        placeDetails.setPlace(selectedPlace);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        Analytics.logSelectContentEvent(String.valueOf(selectedPlace.getId()), selectedPlace.getName(), "place");
    }

    @OnClick(R.id.fab)
    public void onActionButtonClick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            moveToLastLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_ACCESS_LOCATION);
        }
    }

    @OnClick(R.id.place_details)
    public void onPlaceDetailsClick() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            Analytics.logViewContentEvent(String.valueOf(selectedPlace.getId()), selectedPlace.getName(), "place");
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    private class PlacesRenderer extends StaticClusterRenderer<PlaceMarker> {
        PlacesRenderer(Context context, GoogleMap map, ClusterManager<PlaceMarker> clusterManager) {
            super(context, map, clusterManager);
        }

        @Override
        protected void onBeforeClusterItemRendered(PlaceMarker placeMarker, MarkerOptions markerOptions) {
            super.onBeforeClusterItemRendered(placeMarker, markerOptions);

            Place place = placesRepository.getPlace(placeMarker.placeId);

            markerOptions
                    .icon(placeCategoriesMarkersRepository.getPlaceCategoryMarker(place.getCategoryId()))
                    .anchor(Constants.MAP_MARKER_ANCHOR_U, Constants.MAP_MARKER_ANCHOR_V);
        }
    }

    private class CameraChangeListener implements GoogleMap.OnCameraChangeListener {
        @Override
        public void onCameraChange(CameraPosition cameraPosition) {
            refreshMap();
        }
    }

    private class ClusterItemClickListener implements ClusterManager.OnClusterItemClickListener<PlaceMarker> {
        @Override
        public boolean onClusterItemClick(PlaceMarker placeMarker) {
            selectPlace(placeMarker.placeId);
            return false;
        }
    }

    private class LocationApiConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {
        @Override
        public void onConnected(Bundle bundle) {
            if (firstLaunch) {
                firstLaunch = false;
                moveToLastLocation();
            }
        }

        @Override
        public void onConnectionSuspended(int cause) {
            // Nothing to do here
        }
    }

    public class PlaceMarker implements ClusterItem {
        private final long placeId;

        private final double latitude;

        private final double longitude;

        PlaceMarker(long placeId, double latitude, double longitude) {
            this.placeId = placeId;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        @Override
        public LatLng getPosition() {
            return new LatLng(latitude, longitude);
        }
    }
}