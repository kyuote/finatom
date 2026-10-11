package com.finatom.payment.client;

import com.finatom.account.grpc.ValidateWalletRequest;
import com.finatom.account.grpc.ValidateWalletResponse;
import com.finatom.account.grpc.WalletValidationServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.UUID;

public class AccountServiceClient implements KycValidator {

    private final WalletValidationServiceGrpc.WalletValidationServiceBlockingStub stub;

    public AccountServiceClient(String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build();
        this.stub = WalletValidationServiceGrpc.newBlockingStub(channel);
    }

    public void validateKyc(UUID walletId) {
        ValidateWalletResponse response;
        try {
            response = stub.validateWallet(
                ValidateWalletRequest.newBuilder()
                    .setWalletId(walletId.toString())
                    .build()
            );
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new IllegalArgumentException("Wallet not found: " + walletId);
            }
            throw new IllegalStateException("Account service unavailable", e);
        }

        if (!"VERIFIED".equals(response.getKycStatus())) {
            throw new IllegalStateException(
                "KYC not verified for wallet " + walletId + " (status: " + response.getKycStatus() + ")"
            );
        }
    }
}
