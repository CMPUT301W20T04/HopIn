package com.example.hopinnow.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.hopinnow.R;
import com.example.hopinnow.database.RiderDatabaseAccessor;
import com.example.hopinnow.entities.Car;
import com.example.hopinnow.entities.Driver;
import com.example.hopinnow.entities.LatLong;
import com.example.hopinnow.entities.Request;
import com.example.hopinnow.entities.Rider;
import com.example.hopinnow.entities.Trip;
import com.example.hopinnow.helperclasses.QRCodeHelper;
import com.example.hopinnow.statuslisteners.RiderProfileStatusListener;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Author: Tianyu Bai
 * This class defines the activity for rider payment after confirming arrival at drop off location.
 */
public class RiderPaymentActivity extends AppCompatActivity implements RiderProfileStatusListener {
    private Request curRequest;
    private Driver driver;
    private Rider rider;
    private Double totalPayment;
    private Double myTip = 0.00;
    private Double baseFare;
    private ImageView qrImage;
    private Boolean other = false;
    private TextView totalPaymentTextView;
    private Date dropOffDateTime;
    private Double myRating;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rider_payment);

        //TODO set current Request
        SharedPreferences mPrefs = getSharedPreferences("LocalRequest", MODE_PRIVATE);
        Gson gsonRequest = new Gson();
        String json = mPrefs.getString("CurrentRequest", "");
        curRequest = gsonRequest.fromJson(json, Request.class);

        //TODO GET rider FROM FIREBASE, is it needed?
        driver = (Driver) getIntent().getSerializableExtra("Driver");
        rider = (Rider) getIntent().getSerializableExtra("Rider");

        // set local variables
        baseFare = curRequest.getEstimatedFare();
        dropOffDateTime = Calendar.getInstance().getTime();
        totalPayment = baseFare;

        //set initial total payment
        totalPaymentTextView = findViewById(R.id.rider_payment_total);
        totalPaymentTextView.setText(Double.toString(baseFare));

        //show total payment calculation
        Button showTotalBtn = findViewById(R.id.rider_payment_calculate);
        showTotalBtn.setOnClickListener(v -> {
            setMyTip();
            totalPayment = formatTotalPayment();
            totalPaymentTextView.setText(Double.toString(totalPayment));
        });

        // creates QR code on button confirm, QR contains total payment amount
        qrImage = findViewById(R.id.rider_payment_qr);
        final Button confirmPaymentBtn = findViewById(R.id.rider_payment_submit_tips);
        confirmPaymentBtn.setOnClickListener(v -> {

            setMyTip();
            totalPayment = formatTotalPayment();

            //checks available deposit for payment, if enough then QR code is generated
            if (totalPayment > rider.getDeposit()){
                String msg = "There is insufficient deposit in your account!";
                Toast.makeText(RiderPaymentActivity.this,msg,Toast.LENGTH_SHORT).show();
            } else {
                Gson gsonPay = new Gson();
                String encodedMsg= driver.getEmail() + ":" + totalPayment;
                String serializePay = gsonPay.toJson(encodedMsg);
                Bitmap bitmap = QRCodeHelper
                        .newInstance(RiderPaymentActivity.this)
                        .setContent(serializePay)
                        .setMargin(1)
                        .generateQR();
                qrImage.setImageBitmap(bitmap);
                qrImage.setBackgroundResource(R.color.ColorBlack);
                confirmPaymentBtn.setVisibility(View.GONE);
                showTotalBtn.setEnabled(false);

                //onScanningCompleted();
            }
            });

        // get current rider
        RiderDatabaseAccessor riderDatabaseAccessor = new RiderDatabaseAccessor();
        riderDatabaseAccessor.getRiderProfile(this);
    }


    /**
     * Shows dialog that prompts rider to rate the driver of corresponding trip.
     */
    public void showRatingDialog(){
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_rider_rating);

        // set driver name
        final TextView driverName= dialog.findViewById(R.id.dialog_rider_rating_driver);
        driverName.setText(driver.getName());
        driverName.setOnClickListener(v -> {
            showDriverInfo(driver);
        });

        //submit rating and complete request
        final RatingBar ratingBar = dialog.findViewById(R.id.dialog_rating_bar);
        Button submitBtn= dialog.findViewById(R.id.dialog_rating_submit);
        submitBtn.setOnClickListener(v -> {
            myRating = (double) ratingBar.getRating();

            if (myRating!=0){
                setNewDriverRating(myRating);
                completeRequest();
            } else {
                Toast.makeText(RiderPaymentActivity.this, "Please select your " +
                        "rating or press CANCEL to complete your ride.", Toast.LENGTH_SHORT)
                        .show();
            }

        });

        //cancel rating and complete request
        Button cancelBtn= dialog.findViewById(R.id.dialog_rating_cancel);
        cancelBtn.setOnClickListener(v -> completeRequest());

        dialog.show();
    }


    /**
     * Calculates new average rating for driver.
     * @param r
     *      the new rating
     */
    private void setNewDriverRating(double r){
        Double prevRating = driver.getRating();
        int counts = driver.getRatingCounts();
        Double newRating = (prevRating + r)/(counts+1);
        driver.setRatingCounts(counts+1);
        driver.setRating(newRating);
    }


    /**
     * Completes current request and returns rider to the new request prompt page.
     */
    private void completeRequest(){
        String msg = "Your trip is completed!";
        Toast.makeText(RiderPaymentActivity.this, msg, Toast.LENGTH_SHORT).show();

        Trip trip = toTrip();
        //TODO update request completion in firebase inside availableRequest
        //  save request to trip

        // change activity
        Intent intent = new Intent(RiderPaymentActivity.this,RiderMapActivity.class);
        intent.putExtra("Current_Request_To_Null", "cancel");
        startActivity(intent);
    }


    /**
     * Determines the rider selected tip amount.
     * @param view
     *      current view
     */
    public void onRadioButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.rider_payment_ten:
                if (checked)
                    myTip = 0.1;
                    other = false;
                break;
            case R.id.rider_payment_fifteen:
                if (checked)
                    myTip = 0.15;
                    other = false;
                break;
            case R.id.rider_payment_twenty:
                if (checked)
                    myTip = 0.2;
                    other = false;
                break;
            case R.id.rider_payment_else:
                if (checked)
                    other = true;
                    myTip = 0.0;
                break;
        }
    }


    /**
     * Completes payment transaction and pops up rating dialog.
     * This method is trigger by driver finishing the scanning of the QR.
     */
    public void onScanningCompleted(){
        //TODO THIS METHOD TRIGGER BY DRIVER COMPLETE SCANNING, LISTENER ON REQUEST TO TRIP
        double newDepositAmount = rider.getDeposit()-totalPayment;
        rider.setDeposit(newDepositAmount);

        String msg = "Your payment of " + totalPayment + " QR bucks is successful!";
        Toast.makeText(RiderPaymentActivity.this, msg, Toast.LENGTH_SHORT).show();

        showRatingDialog();
    }


    /**
     * Get rider's customized tip amount.
     */
    private void setMyTip(){
        if (other){
            EditText otherTip = findViewById(R.id.rider_payment_other_editText);
            if (!otherTip.getText().toString().isEmpty()) {
                myTip = (Double.parseDouble(otherTip.getText().toString())) / 100;
            }
        }
    }


    /**
     * Format total payment to double with two decimals.
     */
    private Double formatTotalPayment(){
        return Double.parseDouble(new DecimalFormat("##.##")
                .format((1 + myTip) * baseFare));
    }


    /**
     * Change current request from class Request to class Trip.
     */
    private Trip toTrip(){
        int duration = (int) (curRequest.getPickUpDateTime().getTime() - dropOffDateTime.getTime());
        LatLong mpickUpLoc = curRequest.getPickUpLoc();
        //LatLng pickUpLoc = new LatLng(mpickUpLoc.getLat(), mpickUpLoc.getLng());
        LatLong mdropOffLoc = curRequest.getDropOffLoc();
        //LatLng dropOffLoc = new LatLng(mdropOffLoc.getLat(), mdropOffLoc.getLng());
        String dropOffName = curRequest.getDropOffLocName();
        String pickUpName = curRequest.getPickUpLocName();
        Date pickUpTime = curRequest.getPickUpDateTime();
        Car car = driver.getCar();
        return new Trip(driver.getEmail(),rider.getEmail(),mpickUpLoc,mdropOffLoc,pickUpName,
                dropOffName,pickUpTime, dropOffDateTime, duration, car,totalPayment,myRating);
    }

    /**
     * Shows driver information and contact means on a dialog
     */
    public void showDriverInfo(Driver myDriver){
        final Driver d = myDriver;
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_driver_info);

        //set driver name
        TextView driverName= dialog.findViewById(R.id.dialog_driver_name);
        driverName.setText(d.getName());

        //set driver rating
        TextView driverRating = dialog.findViewById(R.id.dialog_driver_rating);
        String rating;
        if (d.getRating()==0){
            rating = "not yet rated";
        } else {
            rating = Double.toString(d.getRating());
        }
        driverRating.setText(rating);

        //set driver car
        TextView driverCar = dialog.findViewById(R.id.dialog_driver_car);
        String carInfo = d.getCar().getColor() + " " + d.getCar().getMake() + " " + d.getCar().getModel();
        driverCar.setText(carInfo);

        //set driver license
        TextView driverLicense = dialog.findViewById(R.id.dialog_driver_plate);
        driverLicense.setText(d.getCar().getPlateNumber());

        //call driver
        Button callBtn= dialog.findViewById(R.id.dialog_call_button);
        callBtn.setOnClickListener(v -> callNumber(d.getPhoneNumber()));

        //email driver
        Button emailBtn= dialog.findViewById(R.id.dialog_email_button);
        emailBtn.setOnClickListener(v -> emailDriver(d.getEmail()));

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    /**
     * Starts phone calling.
     * @param phoneNumber
     *      the phone number to be called
     */
    @SuppressLint("CheckResult")
    public void callNumber(String phoneNumber){
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:"+phoneNumber));

        if (ActivityCompat.checkSelfPermission(RiderPaymentActivity.this,
                Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            RxPermissions rxPermissions = new RxPermissions(this);
            rxPermissions
                    .request(Manifest.permission.CALL_PHONE)
                    .subscribe(granted -> {
                        if (granted) {
                            startActivity(callIntent);
                        }
                    });
        } else {
            String driverNumber = driver.getPhoneNumber();
            Toast.makeText(this,"Driver's Number: " + driverNumber,
                    Toast.LENGTH_LONG).show();
        }
    }


    /**
     * Prompts email app selection and directs to email drafting page with auto0filled email address
     * of the driver.
     * @param email
     *      the driver's email address
     */
    public void emailDriver(String email){
        //Stackoverflow post by Dira
        //https://stackoverflow.com/questions/8701634/send-email-intent
        //Answer by Dira (code from the question itself)
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/html");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] {email});

        startActivity(Intent.createChooser(intent, "Send Email"));
    }


    /**
     * Called when profile retrieve successfully:
     * @param rider
     *      receives a user object containing all info about the current user.
     */
    @Override
    public void onRiderProfileRetrieveSuccess(Rider rider) {
        this.rider = rider;
    }


    /**
     * Called when profile retrieve failed:
     */
    @Override
    public void onRiderProfileRetrieveFailure() {}


    /**
     * Called when profile update successes.
     */
    @Override
    public void onRiderProfileUpdateSuccess(Rider rider) {}


    /**
     * Called when profile update failed:
     */
    @Override
    public void onRiderProfileUpdateFailure() {}

    @Override
    public void onBackPressed(){

    }

}