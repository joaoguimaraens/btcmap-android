package com.bubelov.coins.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bubelov.coins.R;
import com.bubelov.coins.loader.MerchantsLoader;
import com.bubelov.coins.model.Merchant;
import com.bubelov.coins.ui.fragment.CurrenciesFilterDialogFragment;
import com.bubelov.coins.ui.widget.DrawerMenu;
import com.bubelov.coins.util.OnCameraChangeMultiplexer;
import com.bubelov.coins.util.StaticClusterRenderer;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.ClusterManager;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class MapActivity extends AbstractActivity implements LoaderManager.LoaderCallbacks<Cursor>, DrawerMenu.OnMenuItemSelectedListener, CurrenciesFilterDialogFragment.Listener {
    private static final int MERCHANTS_LOADER = 0;

    private DrawerLayout drawer;

    private ActionBarDrawerToggle drawerToggle;

    private GoogleMap map;

    private ClusterManager<Merchant> merchantsManager;

    private String amenity;

    private SlidingUpPanelLayout slidingLayout;

    private View merchantHeader;

    private TextView merchantName;

    private TextView merchantAddress;

    private TextView merchantDescription;

    private ImageView callMerchantView;

    private ImageView openMerchantWebsiteView;

    private ImageView shareMerchantView;

    public static Intent newShowMerchantIntent(Context context, double latitude, double longitude) {
        return new Intent(context, MapActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("All");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerToggle = new DrawerToggle(this, drawer, android.R.string.ok, android.R.string.ok);
        drawer.setDrawerListener(drawerToggle);

        DrawerMenu drawerMenu = (DrawerMenu) findViewById(R.id.left_drawer);
        drawerMenu.setSelected(R.id.all);
        drawerMenu.setItemSelectedListener(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        map = mapFragment.getMap();
        map.setMyLocationEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setCompassEnabled(false);

        initClustering();

        map.setOnCameraChangeListener(new OnCameraChangeMultiplexer(merchantsManager, new CameraChangeListener()));

        LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
        getSupportLoaderManager().initLoader(MERCHANTS_LOADER, MerchantsLoader.prepareArguments(bounds, amenity), this);

        merchantHeader = findView(R.id.merchant_header);
        merchantName = findView(R.id.merchant_name);
        merchantAddress = findView(R.id.merchant_address);
        merchantDescription = findView(R.id.merchant_description);

        callMerchantView = findView(R.id.call_merchant);
        openMerchantWebsiteView = findView(R.id.open_merchant_website);
        shareMerchantView = findView(R.id.share_merchant);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();

        slidingLayout = findView(R.id.sliding_panel);
        slidingLayout.setPanelHeight(merchantName.getHeight());
        slidingLayout.setAnchorPoint(0.5f);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        int id = item.getItemId();

        switch (id) {
            case R.id.action_filter:
                new CurrenciesFilterDialogFragment().show(getSupportFragmentManager(), CurrenciesFilterDialogFragment.TAG);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == MERCHANTS_LOADER) {
            return new MerchantsLoader(this, args);
        } else {
            return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Collection<Merchant> merchants = new ArrayList<>();

        while (data.moveToNext()) {
            Merchant merchant = new Merchant();
            merchant.setId(data.getLong(0));
            merchant.setLatitude(data.getFloat(1));
            merchant.setLongitude(data.getFloat(2));
            merchant.setName(data.getString(3));
            merchant.setDescription(data.getString(4));

            merchants.add(merchant);
        }

        merchantsManager.clearItems();
        merchantsManager.addItems(merchants);
        merchantsManager.cluster();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        merchantsManager.clearItems();
        merchantsManager.cluster();
    }

    @Override
    public void onMenuItemSelected(int id, com.bubelov.coins.ui.widget.MenuItem menuItem) {
        drawer.closeDrawer(Gravity.LEFT);

        if (id != R.id.settings && id != R.id.help && id != R.id.donate) {
            getSupportActionBar().setTitle(menuItem.getText());
        }

        if (id == R.id.settings) {
            startActivity(new Intent(this, SettingsActivity.class), ActivityOptionsCompat.makeSceneTransitionAnimation(this).toBundle());
            return;
        }

        switch (id) {
            case R.id.all:
                amenity = null;
                break;
            case R.id.atms:
                amenity = "atm";
                break;
            case R.id.cafes:
                amenity = "cafe";
                break;
            case R.id.restaurants:
                amenity = "restaurant";
                break;
            case R.id.bars:
                amenity = "bar";
                break;
            case R.id.hotels:
                amenity = "TODO";
                break;
            case R.id.car_washes:
                amenity = "car_wash";
                break;
            case R.id.gas_stations:
                amenity = "fuel";
                break;
            case R.id.hospitals:
                amenity = "hospital";
                break;
            case R.id.laundry:
                amenity = "TODO";
                break;
            case R.id.movies:
                amenity = "cinema";
                break;
            case R.id.parking:
                amenity = "parking";
                break;
            case R.id.pharmacies:
                amenity = "pharmacy";
                break;
            case R.id.pizza:
                amenity = "TODO";
                break;
            case R.id.taxi:
                amenity = "taxi";
                break;
        }

        reloadMerchants();
    }

    @Override
    public void onCurrenciesFilterDismissed() {
        reloadMerchants();
    }

    private void initClustering() {
        merchantsManager = new ClusterManager<>(this, map);
        PlacesRenderer renderer = new PlacesRenderer(this, map, merchantsManager);
        merchantsManager.setRenderer(renderer);
        renderer.setOnClusterItemClickListener(new ClusterItemClickListener());

        map.setOnCameraChangeListener(merchantsManager);
        map.setOnMarkerClickListener(merchantsManager);
        map.setOnMapClickListener(latLng -> slidingLayout.setPanelHeight(0));
    }

    private void reloadMerchants() {
        LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
        getSupportLoaderManager().restartLoader(MERCHANTS_LOADER, MerchantsLoader.prepareArguments(bounds, amenity), MapActivity.this);
    }

    private class DrawerToggle extends ActionBarDrawerToggle {
        public DrawerToggle(Activity activity, DrawerLayout drawerLayout, int openDrawerContentDescRes, int closeDrawerContentDescRes) {
            super(activity, drawerLayout, openDrawerContentDescRes, closeDrawerContentDescRes);
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            super.onDrawerOpened(drawerView);
            invalidateOptionsMenu();
        }

        @Override
        public void onDrawerClosed(View drawerView) {
            super.onDrawerClosed(drawerView);
            invalidateOptionsMenu();
        }
    }

    private class PlacesRenderer extends StaticClusterRenderer<Merchant> {
        public PlacesRenderer(Context context, GoogleMap map, ClusterManager<Merchant> clusterManager) {
            super(context, map, clusterManager);
        }

        @Override
        protected void onBeforeClusterItemRendered(Merchant item, MarkerOptions markerOptions) {
            super.onBeforeClusterItemRendered(item, markerOptions);
            markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_place_white_48dp));
        }
    }

    private class CameraChangeListener implements GoogleMap.OnCameraChangeListener {
        @Override
        public void onCameraChange(CameraPosition cameraPosition) {
            reloadMerchants();
        }
    }

    private class ClusterItemClickListener implements ClusterManager.OnClusterItemClickListener<Merchant> {
        @Override
        public boolean onClusterItemClick(Merchant merchant) {
            merchantName.setText(merchant.getName());
            merchantDescription.setText(merchant.getDescription());
            slidingLayout.setPanelHeight(merchantHeader.getHeight());

            if (TextUtils.isEmpty(merchant.getPhone())) {
                callMerchantView.setColorFilter(getResources().getColor(R.color.icons));
            } else {
                callMerchantView.setColorFilter(getResources().getColor(R.color.primary));
            }

            if (TextUtils.isEmpty(merchant.getWebsite())) {
                openMerchantWebsiteView.setColorFilter(getResources().getColor(R.color.icons));
            } else {
                openMerchantWebsiteView.setColorFilter(getResources().getColor(R.color.primary));
            }

            shareMerchantView.setOnClickListener(v -> showToast("TODO"));

            new LoadAddressTask().execute(merchant.getPosition());

            return false;
        }
    }

    private class LoadAddressTask extends AsyncTask<LatLng, Void, String> {
        @Override
        protected void onPreExecute() {
            merchantAddress.setText("Loading address...");
        }

        @Override
        protected String doInBackground(LatLng... params) {
            Geocoder geo = new Geocoder(getApplicationContext(), Locale.getDefault());

            List<Address> addresses = null;

            try {
                addresses = geo.getFromLocation(params[0].latitude, params[0].longitude, 1);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (addresses.isEmpty()) {
                merchantName.setText("Waiting for Location");
            } else {
                Address address = addresses.get(0);

                if (addresses.size() > 0) {
                    String addressText = String.format("%s, %s, %s",
                            address.getMaxAddressLineIndex() > 0 ? address.getAddressLine(0) : "",
                            address.getLocality(),
                            address.getCountryName());

                    return addressText;
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(String address) {
            if (TextUtils.isEmpty(address)) {
                merchantAddress.setText("Couldn't load address");
            } else {
                merchantAddress.setText(address);
            }
        }
    }
}