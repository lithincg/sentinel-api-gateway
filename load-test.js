import http from 'k6/http';
import { sleep } from 'k6';

export const options = {
    stages: [
        { duration: "5s", target: 10 },
        { duration: "5s", target: 15 },
        { duration: "5s", target: 20 },
        { duration: "2m", target: 20 },
    ]
};

const keys = [
    "54139b7b96764f5b91b6359a0b30f283",
    "b8d9e194cc884909a0beeb7621bcb6be",
    "a520b5b6ed7546c59af1cf2b6cd3357e",
    "e6c51d49d7304b2faaf8857209b06951",
    "21f79efd99754c308994cca110b4df73",
    "342cd0bf236745cd94180f28e4c11c63",
    "b7d6b0f8281e4232ad14a68ec4f52fa2",
    "9185510107124cf2bc36639995c0f74c",
    "b6df520bf40e407bbe350c6bc0effacc",
    "124ebf2f920e44e3a68d35f51f39d5f9",
    "ea9bdd7171884e38ba9dfa132118948f",
    "6083e6eccabb4fc3bb4bc6345393a969",
    "b4631245cbe2484595be2e2f22dd8871",
    "90a96b374a864a38b4554192ea6e487d",
    "ca79fc71553b4026b308e24003039948",
    "def319885c8640c89a92f294a7b1378f",
    "fe938f735b234470997dd8a32860af52",
    "b20c26c3c1ae49b0a7dd185edaa984dc",
    "b4275ef08c744cc58b7fa75f631e22bf",
    "0ea3f967b44740858207dfed86d7f72a"
];

export default function() {
    const rawKey = keys[__VU - 1];
    http.get("http://localhost:8080/api/dummy", { headers: { "X-API-KEY": rawKey } });
    sleep(0.4);
}