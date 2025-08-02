package com.ninadkhire.gdrivebackuptest;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private AuthorizationResult authorizationResult;
    private Account chosenAccount;

    private TextView tv;
    private TextView selectedAccountTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv = findViewById(R.id.textView);
        selectedAccountTextView = findViewById(R.id.selectedAccountTextView);

        // Initialize the ActivityResultLauncher
        chooseAccountLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        // User successfully selected an account
                        String accountName = result.getData().getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                        String accountType = result.getData().getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
                        chosenAccount = new Account(accountName, accountType);

                        if (accountName != null && accountType != null) {
                            selectedAccountTextView.setText(accountName);
                            Toast.makeText(this, "Account selected: " + accountName + "(" + accountType + ")", Toast.LENGTH_SHORT).show();
                            // Here you can proceed with using the selected account, e.g.,
                            // request an authentication token if needed.
                        } else {
                            selectedAccountTextView.setText("Account data is null.");
                            Toast.makeText(this, "Failed to get account details.", Toast.LENGTH_SHORT).show();
                        }
                    } else if (result.getResultCode() == RESULT_CANCELED) {
                        // User cancelled the account selection
                        selectedAccountTextView.setText("Account selection cancelled.");
                        Toast.makeText(this, "Account selection cancelled.", Toast.LENGTH_SHORT).show();
                    } else {
                        // Other error
                        selectedAccountTextView.setText("Account selection failed.");
                        Toast.makeText(this, "Account selection failed with code: " + result.getResultCode(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    public void saveFileToDrive(final View view) {
        authorizeAccess(101, (result) -> {
            new Thread(() -> {
                try {
                    saveToDriveAppFolder(authorizationResult);
                } catch (IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "IOException: " + e, Toast.LENGTH_SHORT).show();
                        tv.setText("IOException: " + e);
                    });
                }
            }).start();
        });
    }

    public void listAppData(final View view) {
        Toast.makeText(this, "Showing App Data files", Toast.LENGTH_SHORT).show();
        authorizeAccess(102, (result) -> {
            new Thread(() -> {
                try {
                    listAppData(authorizationResult);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        });
    }

    public void readAppDataFile(final View view) {
        Toast.makeText(this, "Reading App Data files", Toast.LENGTH_SHORT).show();
        authorizeAccess(103, (result) -> {
            new Thread(() -> {
                try {
                    readAppDataFile(authorizationResult);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        });
    }

    public void authorizeAccess(final View view) {
        authorizeAccess(100, (result) -> {
            Toast.makeText(this, "Authorization successful", Toast.LENGTH_SHORT).show();
        });
    }

    // ActivityResultLauncher for handling the result from newChooseAccountIntent
    private ActivityResultLauncher<Intent> chooseAccountLauncher;

    public void chooseAccount(View view) {
        // Create the Intent to choose an account
        Intent intent = AccountManager.newChooseAccountIntent(
                null, // A current account (optional, null means no pre-selection)
                null, // A list of disallowed accounts (optional, null means all are allowed)
                new String[]{"com.google"}, // Array of desired account types (e.g., Google, WhatsApp)
                null, // Optional bundle of account authenticator options
                null, // Optional array of account authenticator features
                null, // Optional bundle of add account options
                null  // Optional callback
        );

        // Launch the activity using the ActivityResultLauncher
        chooseAccountLauncher.launch(intent);
    }

    public void authorizeAccess(final int requestCode, final Consumer<AuthorizationResult> authorizationResultConsumer) {
        if (Objects.isNull(chosenAccount)) {
            Log.w(TAG, "authorizeAccess: Please first choose the account for authorization.");
            Toast.makeText(this, "Please first choose the account for authorization.", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Scope> requestedScopes = Collections.singletonList(new Scope(DriveScopes.DRIVE_APPDATA));
        Toast.makeText(this, "Requesting authorization for account " + chosenAccount.name, Toast.LENGTH_SHORT).show();
        AuthorizationRequest authorizationRequest = AuthorizationRequest.builder()
                .setRequestedScopes(requestedScopes)
                .setAccount(chosenAccount)
                .build();
        Identity.getAuthorizationClient(this)
                .authorize(authorizationRequest)
                .addOnSuccessListener(
                        authorizationResult -> {
                            if (authorizationResult.hasResolution()) {
                                // Access needs to be granted by the user
                                PendingIntent pendingIntent = authorizationResult.getPendingIntent();
                                try {
                                    startIntentSenderForResult(pendingIntent.getIntentSender(),
                                            requestCode, null, 0, 0, 0, null);
                                } catch (IntentSender.SendIntentException e) {
                                    Log.e(TAG, "Couldn't start Authorization UI: " + e.getLocalizedMessage());
                                }
                            } else {
                                // Access already granted, continue with user action
                                this.authorizationResult = authorizationResult;
                                authorizationResultConsumer.accept(authorizationResult);
                            }
                        })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to authorize", e);
                    Toast.makeText(this, "Failed to get authorization from user.", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final AuthorizationResult finalAuthResult = authorizationResult;
        if (requestCode == 100) {
            try {
                authorizationResult = Identity.getAuthorizationClient(this)
                        .getAuthorizationResultFromIntent(data);
            } catch (ApiException e) {
                Log.e(TAG, "ApiException during getAuthorizationResultFromIntent: " + e);
            }
            Log.i(TAG, "onActivityResult: User ID: " + authorizationResult.toGoogleSignInAccount().getId());
            Log.i(TAG, "onActivityResult: Email: " + authorizationResult.toGoogleSignInAccount().getEmail());
            Log.i(TAG, "onActivityResult: Display Name: " + authorizationResult.toGoogleSignInAccount().getDisplayName());
            Log.i(TAG, "onActivityResult: Requested Scopes: " + authorizationResult.toGoogleSignInAccount().getRequestedScopes());
            Toast.makeText(this,
                    "Got authorization result for account " +
                            authorizationResult.toGoogleSignInAccount().getEmail(),
                    Toast.LENGTH_SHORT).show();
        } else if (requestCode == 101) {
            try {
                authorizationResult = Identity.getAuthorizationClient(this)
                        .getAuthorizationResultFromIntent(data);
            } catch (ApiException e) {
                Log.e(TAG, "ApiException during getAuthorizationResultFromIntent: " + e);
            }
            new Thread(() -> {
                try {
                    saveToDriveAppFolder(finalAuthResult);
                } catch (IOException e) {
                    runOnUiThread(() -> tv.setText("IOException: " + e));
                }
            }).start();
        } else if (requestCode == 102) {
            try {
                authorizationResult = Identity.getAuthorizationClient(this)
                        .getAuthorizationResultFromIntent(data);
            } catch (ApiException e) {
                Log.e(TAG, "ApiException during getAuthorizationResultFromIntent: " + e);
            }
            new Thread(() -> {
                try {
                    listAppData(finalAuthResult);
                } catch (IOException e) {
                    runOnUiThread(() -> tv.setText("IOException: " + e));
                }
            }).start();
        } else if (requestCode == 103) {
            try {
                authorizationResult = Identity.getAuthorizationClient(this)
                        .getAuthorizationResultFromIntent(data);
            } catch (ApiException e) {
                Log.e(TAG, "ApiException during getAuthorizationResultFromIntent: " + e);
            }
            new Thread(() -> {
                try {
                    readAppDataFile(finalAuthResult);
                } catch (IOException e) {
                    runOnUiThread(() -> tv.setText("IOException: " + e));
                }
            }).start();
        }
    }

    private void saveToDriveAppFolder(AuthorizationResult authorizationResult) throws IOException {
        GoogleCredentials googleCredentials =
                GoogleCredentials.create(new AccessToken(authorizationResult.getAccessToken(),
                        new Date(2099, 12, 31))) // Setting a future date
                        .createScoped(DriveScopes.DRIVE_APPDATA);
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(googleCredentials);
        Drive service = new Drive.Builder(new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer)
                .setApplicationName("GDriveBackupTestApp")
                .build();
        try {
            // File's metadata.
            File fileMetadata = new File();
            fileMetadata.setName("testfile.txt");
            fileMetadata.setParents(Collections.singletonList("appDataFolder"));
            // Create local file
            java.io.File localFile = new java.io.File(MainActivity.this.getFilesDir(), "testfile.txt");
            boolean res = localFile.createNewFile();
            runOnUiThread(() -> Toast.makeText(this, "File creation res: " + res, Toast.LENGTH_SHORT).show());
            FileWriter writer = new FileWriter(localFile);
            writer.write("Hello Google Drive!");
            writer.close();
            // Upload the file
            FileContent mediaContent = new FileContent("application/txt", localFile);
            File uploadedFile = service.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute();
            System.out.println("File ID: " + uploadedFile.getId());
            tv.post(() -> Toast.makeText(this, "Uploaded file with ID: " + uploadedFile.getId(), Toast.LENGTH_SHORT).show());
            // Delete outdated backup files except the uploadedFile to keep only the latest file in the Drive.
            FileList fileList = service.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name = 'testfile.txt'")
                    .setFields("nextPageToken,files(id,name)")
                    .setPageSize(50)
                    .execute();
            for (com.google.api.services.drive.model.File file: fileList.getFiles()) {
                if (!Objects.equals(file.getId(), uploadedFile.getId())) {
                    service.files().delete(file.getId()).execute();
                    Log.i(TAG, "saveToDriveAppFolder: Deleted outdated backup file with id: " + file.getId());
                }
            }
        } catch (GoogleJsonResponseException exception) {
            System.err.println("Unable to create file: " + exception.getDetails());
            tv.post(() -> tv.setText("Unable to create file: " + exception.getDetails()));
        }
    }

    private void listAppData(AuthorizationResult authorizationResult) throws IOException {
        GoogleCredentials googleCredentials =
                GoogleCredentials.create(new AccessToken(authorizationResult.getAccessToken(),
                                new Date(2099, 12, 31)))
                        .createScoped(DriveScopes.DRIVE_APPDATA);
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(googleCredentials);

        // Build a new authorized API client service.
        Drive service = new Drive.Builder(new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer)
                .setApplicationName("GDriveBackupTestApp")
                .build();

        try {
            FileList files = service.files().list()
                    .setSpaces("appDataFolder")
                    .setOrderBy("createdTime desc")
                    .setFields("nextPageToken, files(id, name)")
                    .setPageSize(10)
                    .execute();
            StringBuilder fileList = new StringBuilder();
            for (File file : files.getFiles()) {
                System.out.printf("Found file: %s (%s)\n",
                        file.getName(), file.getId());
                fileList.append("\nFound file: " + file.getName() + " " + file.getId());
            }
            tv.post(() -> {
                tv.setText(fileList);
                Toast.makeText(this, "Found " + files.getFiles().size() + " files.", Toast.LENGTH_SHORT).show();
            });
        } catch (GoogleJsonResponseException e) {
            System.err.println("Unable to list files: " + e.getDetails());
            tv.post(() -> tv.setText("Unable to list files: " + e.getDetails()));
        }
    }

    private void readAppDataFile(AuthorizationResult authorizationResult) throws IOException {
        GoogleCredentials googleCredentials =
                GoogleCredentials.create(new AccessToken(authorizationResult.getAccessToken(),
                        new Date(2099, 12, 31)))
                        .createScoped(DriveScopes.DRIVE_APPDATA);
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(googleCredentials);

        // Build a new authorized API client service.
        Drive service = new Drive.Builder(new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer)
                .setApplicationName("GDriveBackupTestApp")
                .build();

        try {
            FileList files = service.files().list()
                    .setSpaces("appDataFolder")
                    .setOrderBy("createdTime desc")
                    .setFields("nextPageToken, files(id, name)")
                    .setPageSize(10)
                    .execute();

            if (files.getFiles().isEmpty()) {
                tv.post(() -> Toast.makeText(this, "No files found in the chosen Drive.", Toast.LENGTH_SHORT).show());
                return;
            }

            File appDataFile = files.getFiles().get(0);
            String fileID = appDataFile.getId();
            String fileName = appDataFile.getName();

            OutputStream outputStream = new ByteArrayOutputStream();

            service.files()
                    .get(files.getFiles().get(0).getId())
                    .executeMediaAndDownloadTo(outputStream);

            tv.post(() -> tv.setText("Read file " + fileName + "(" + fileID + ")\n" +
                    new String(((ByteArrayOutputStream)outputStream).toByteArray())));
        } catch (GoogleJsonResponseException e) {
            System.err.println("Unable to list files: " + e.getDetails());
            tv.post(() -> tv.setText("Unable to list files: " + e.getDetails()));
        }
    }
}