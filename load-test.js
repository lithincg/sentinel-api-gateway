import http from 'k6/http';
import { sleep } from 'k6';

export const options = {
    stages: [
        { duration: "10s", target: 25 },
        { duration: "10s", target: 50 },
        { duration: "10m", target: 50 },
    ],
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
    "0ea3f967b44740858207dfed86d7f72a",
    "b4631245cbe2484595be2e2f224d8871",
    "90a96b374a864a38b4554192ea4e487d",
    "ca79fc71553b4026b308e24003439948",
    "def319885c8640c89a92f294a741378f",
    "fe938f735b234470997dd8a32840af52",
    "b20c26c3c1ae49b0a7dd185eda4984dc",
    "b4275ef08c744cc58b7fa75f634e22bf",
    "0ea3f967b44740858207dfed8647f72a",
    "77a8fc55335b409395a5d2eb142a06a3",
    "9fc0c81748bb45d5812f56545ecc2e83",
    "28557a34f7184510b8df6377c78725c7",
    "3d7f9879efb74b30ab5ff60de71ee839",
    "243a934ec2124982828eb20f53e69e15",
    "cce21916c51143e9bf7850ccabbae1a8",
    "a8f88073b8114b44be159bf4dc046ba7",
    "eb4d03f610aa4588ac0b0ad263ce03b7",
    "78020c6fc61b4089aa681884570863bb",
    "52cf17d6dde64a9d85d0783002287c35",
    "805c20b6f1c046c293a062a6164beedf",
    "dbe4fc5c6b6849adb1d63479f1a7b6db",
    "ee8b6b0f15604946b0cb51165e2d65a7",
    "c3f32fb0941b4202bf70d873b346762a",
    "982d930bdbea4999a580444bf1835eb3",
    "851bbf43e3a641e1a243117a855ab9e8",
    "64947e179e0c4efcb5ec776871382b69",
    "15f71d59e4544f18b846992d5aaa8d0d",
    "babe8543e2014e69bcf60c3a409058c7",
    "2e5eb80383174a0d93af2a220ee369f9",
    "ce4453451c5646faad042d6e1efee1e7",
    "ef6fb9e5da4847d18cd967864b5d7935"
];

export default function() {
    const rawKey = keys[__VU - 1];
    http.get("http://localhost:8080/api/dummy", { headers: { "X-API-KEY": rawKey } });
    sleep(0.0);
}