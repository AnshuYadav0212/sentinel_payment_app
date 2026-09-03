import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 40,
    duration: '30s',
};

export default function () {
    const payload = JSON.stringify({
        senderAccountNumber: '808948825683',
        receiverAccountNumber: '629633341145',
        amount: 100
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const response = http.post(
        'http://localhost:8082/api/v1/transactions/transfer',
        payload,
        params
    );

    check(response, {
        'status is not 500': (r) => r.status !== 500,
    });

    sleep(1);
}