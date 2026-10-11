package com.finatom.account.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.jooq.DSLContext;

import java.util.UUID;

public class WalletValidationGrpcService extends WalletValidationServiceGrpc.WalletValidationServiceImplBase {

    private final DSLContext dsl;

    public WalletValidationGrpcService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void validateWallet(ValidateWalletRequest request,
                                StreamObserver<ValidateWalletResponse> responseObserver) {
        UUID walletId;
        try {
            walletId = UUID.fromString(request.getWalletId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("Invalid wallet id: " + request.getWalletId())
                .asRuntimeException());
            return;
        }

        var record = dsl.fetchOne(
            "SELECT a.id AS account_id, a.kyc_status " +
            "FROM wallets w JOIN accounts a ON w.account_id = a.id " +
            "WHERE w.id = ?",
            walletId
        );

        if (record == null) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription("Wallet not found: " + walletId)
                .asRuntimeException());
            return;
        }

        responseObserver.onNext(ValidateWalletResponse.newBuilder()
            .setAccountId(record.get("account_id", UUID.class).toString())
            .setKycStatus(record.get("kyc_status", String.class))
            .build());
        responseObserver.onCompleted();
    }
}
