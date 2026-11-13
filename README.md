<div align="center">

<img src="https://i.imgur.com/LvJD97T.png" alt="logo" style="width:25%">

# Ymir

<div align="center" >
  <img align="center" src="https://img.shields.io/badge/version-1.0.0 (abcdef)-blue">
  <img align="center" src="https://img.shields.io/badge/minecraft-forge 1.7.10-blue">
  <img align="center" src="https://img.shields.io/badge/maintainer-Zeldown-orange">
  <img align="center" src="https://img.shields.io/maintenance/yes/9999">
</div>

<br>

Créez, chargez, copiez et supprimez des mondes Minecraft pendant que le serveur tourne, en une ligne.
<br><br>
Ymir paie à votre place tous les pièges du multi-monde sur Forge et Bukkit : dimensions fantômes, fichiers de région jamais relâchés, identifiants de dimension qui se télescopent, mondes vides ignorés par les mods.

[Pourquoi](#pourquoi) • [Utilisation](#utilisation) • [Options](#options) • [Gradle](#gradle) • [Build](#build) • [Publish](#publish)

</div>

## Pourquoi

Créer un monde à chaud, c'est trois lignes. Le faire sans rien casser, beaucoup moins. Ymir règle ce que l'on découvre en production :

- **Plus de monde fantôme.** Une dimension déchargée est désinscrite. Sans ça, un joueur qui se reconnecte force le serveur à recréer sa dimension par le chemin vanilla, dans un dossier `DIM<id>` parallèle au vôtre, qui reste chargé et sauvegardé à vie.
- **Plus de conflit d'identifiants.** L'identifiant de dimension est relâché à chaque chargement, donc deux mondes ne peuvent jamais revendiquer le même numéro, quoi qu'il arrive au redémarrage.
- **Fichiers réellement relâchés.** Les fichiers de région restent ouverts après un déchargement : la suppression échoue sur NFS et un monde recréé au même endroit ressert les anciens chunks. Ymir vide la file d'écriture puis ferme et retire du cache les fichiers du monde.
- **Suppression sans course.** Le dossier est renommé dans une corbeille avant d'être effacé en arrière-plan : on peut recréer un monde du même nom immédiatement.
- **Un vide vraiment vide.** Le type `VOID` est un vrai type de monde Forge, pas un générateur Bukkit — ce dernier est ignoré dès qu'un mod fournit le provider du monde. Les chunks sont marqués décorés, donc aucun mod ne vient y semer de minerai.
- **Déchargement automatique** des mondes inactifs, et rien à faire à l'arrêt du serveur.
- **Aussi sans Bukkit.** Sur un serveur Forge seul, comme l'environnement de développement, Ymir crée, charge et téléporte lui-même, avec la même API et les mêmes dossiers sur le disque. Comme sous Bukkit, un monde ne se décharge jamais de lui-même : seul `unloadAfter`, `deleteAfter` ou un appel à `unload` le décharge.

## Utilisation

### Créer

```java
final YmirWorld world = Ymir.create(YmirWorldConfig.create("arene")
    .type(YmirWorldType.VOID)
    .platform(4)
    .gamerule("keepInventory", true)
    .unloadAfter(Duration.ofMinutes(5)));

world.teleport(player);
```

`create` construit le monde s'il n'existe pas et le charge sinon, avec la même configuration. Le monde se décharge seul après cinq minutes sans personne, et se recharge à la première téléportation.

### Retrouver

```java
Ymir.get("arene");          // par nom
Ymir.get(player);           // le monde Ymir où se trouve un joueur, s'il y en a un
Ymir.get(player.worldObj);  // idem à partir d'un monde Minecraft
Ymir.getWorlds();           // tout ce qu'Ymir connaît
Ymir.exists("arene");       // le dossier est-il sur le disque
```

### Copier, supprimer

```java
world.copy("arene-2").thenAccept(copy -> copy.teleport(player));
world.delete();
```

La copie se fait hors du thread serveur et vous rend la main dessus : la suite du `thenAccept` s'exécute là où vous pouvez toucher au jeu. La suppression exige un monde vide de joueurs, sinon elle lève une `YmirWorldException`.

Les deux existent aussi à partir d'une simple configuration, sans jamais charger le monde :

```java
Ymir.copy(YmirWorldConfig.create("arene"), YmirWorldConfig.create("arene-2"));
Ymir.delete(YmirWorldConfig.create("arene"));
```

Un monde chargé est déchargé proprement avant l'opération, un monde absent du serveur est traité directement sur le disque. C'est la forme à utiliser pour régénérer ou archiver un monde que personne n'occupe.

N'appelez jamais `.join()` sur ces futures depuis le thread serveur : elles se terminent dessus, vous attendriez votre propre tick. Enchaînez avec `thenAccept` ou `whenComplete`.

### Partir d'un modèle

```java
Ymir.createAsync(YmirWorldConfig.create("donjon-" + id)
    .template(new File(server, "templates/donjon"))
    .unloadAfter(Duration.ofMinutes(1)))
    .thenAccept(donjon -> donjon.teleport(player));
```

`createAsync` copie le modèle hors du thread serveur, puis crée le monde sur le thread serveur.

### Mondes jetables

```java
Ymir.createAsync(YmirWorldConfig.create("donjon-" + id)
    .template(new File(server, "templates/donjon"))
    .temporary(true)
    .deleteAfter(Duration.ofSeconds(30))
    .preload(3))
    .thenAccept(donjon -> donjon.teleport(player));
```

Un monde `temporary` n'est jamais sauvegardé et, s'il reste sur le disque après un arrêt brutal, il est supprimé au démarrage suivant avec sa configuration WorldGuard. `deleteAfter` le supprime quand il est resté vide ce temps : un joueur qui se reconnecte avant retrouve son monde. `preload` charge les chunks autour du spawn avant de rendre le monde, sans dépasser un budget de chunks par tick partagé par tous les mondes.

### Précharger

```java
world.preload(chunkX, chunkZ, 2).thenAccept(loaded -> loaded.teleport(player, x, y, z, yaw, pitch));
```

Les chunks demandés se chargent quelques-uns par tick, depuis le disque en arrière-plan quand ils existent : le joueur arrive dans une zone déjà chargée, sans gel du serveur.

### Suivre le cycle de vie

```java
@SubscribeEvent
public void onCreate(final YmirWorldEvent.Create event) {
    event.getWorld().setGamerule("doDaylightCycle", false);
}
```

`Create`, `Load`, `Unload` et `Delete` sont postés sur le bus Forge, ainsi que `YmirPlayerEvent.Enter` et `YmirPlayerEvent.Leave` quand un joueur entre dans un monde Ymir ou en sort, connexion, déconnexion, mort et téléportation comprises.

## Options

Toutes se déclarent sur `YmirWorldConfig.create(nom)`.

| Option | Défaut | Rôle |
|---|---|---|
| `type` | `NORMAL` | `NORMAL`, `FLAT`, `AMPLIFIED`, `LARGE_BIOMES` ou `VOID` |
| `seed` | aléatoire | graine, numérique ou textuelle |
| `structures` | `true` | génération des structures |
| `environment` | `NORMAL` | `NORMAL`, `NETHER` ou `THE_END` |
| `spawn` | selon le type | point d'apparition imposé, sans la recherche vanilla (aussi évitée pour `VOID` et `FLAT`) |
| `platform` | aucune | plateforme carrée de ce rayon sous le spawn, utile pour un monde vide |
| `gamerule` | aucune | gamerules appliquées à chaque chargement |
| `unloadAfter` | jamais | déchargement après ce temps sans joueur |
| `deleteAfter` | jamais | suppression après ce temps sans joueur |
| `temporary` | `false` | monde jetable : jamais sauvegardé, supprimé au démarrage s'il est resté sur le disque |
| `preload` | aucun | rayon de chunks chargés autour du spawn avant que `createAsync` rende le monde |
| `time` | cycle normal | heure figée, réappliquée à chaque chargement |
| `weather` | `true` | `false` garde un temps clair |
| `keepSpawnInMemory` | `false` | garde les chunks du spawn chargés (Bukkit seulement) |
| `autoSave` | `true` | sauvegarde automatique |
| `template` | aucun | dossier copié au lieu de générer le monde |
| `link` | aucun | dossier qui suit le monde, copié et supprimé avec lui, résolu depuis son nom |
| `worldGuard` | `false` | copie la configuration WorldGuard du monde principal et charge les régions du nouveau monde |
| `chat` | aucun | chat propre au monde et accès aux chats serveur et global, appliqués par PalaChats |
| `tab` | `SERVER` | joueurs affichés dans le tab, appliqués par le plugin Tab |

Le chat et le tab se décrivent dans Ymir et sont appliqués par les mods qui les affichent :

```java
YmirWorldConfig.create("donjon-" + id)
    .chat(YmirWorldChat.create()
        .name("dungeon")
        .server(false)
        .prefix(YmirWorldChatPrefix.emote("dungeon")))
    .tab(YmirWorldTab.create(YmirWorldTabScope.WORLD));
```

| Option du chat | Défaut | Rôle |
|---|---|---|
| `name` | aucun | nom de l'onglet du chat du monde, réservé aux joueurs présents dans le monde et sélectionné à l'entrée ; sans nom, pas de chat de monde |
| `server` | `true` | accès au chat serveur ; `false` le coupe dans les deux sens |
| `global` | `true` | accès au chat global ; `false` le coupe dans les deux sens |
| `prefix` | celui du serveur | `none()`, `emote(clé)` de la configuration d'emotes ou `url(lien)` d'une image, devant les messages envoyés depuis le monde |

| Option du tab | Défaut | Rôle |
|---|---|---|
| `create(portée)` | | `SERVER` tout le serveur, `WORLD` les joueurs du monde, `SELF` soi seul |
| `visible` | `true` | `false` retire les joueurs du monde du tab des joueurs de l'extérieur |

Un dossier lié se déclare par une fonction, pour qu'Ymir sache le nommer quand vous copiez le monde :

```java
YmirWorldConfig.create("arene").link(name -> new File(Ymir.getContainer(), "data/" + name));
```

## Gradle

To include Ymir in your project using Gradle, add the following dependency to your `build.gradle` file:

```gradle
repositories {
    maven {
        name = "forge"
        url = "http://repository.palagitium.dev/artifactory/Paladium-Dev"
        credentials {
            username = getRepositoryUser()
            password = getRepositoryPassword()
        }
    }
}

def getRepositoryUser() {
    if(System.getenv('MAVEN_REPO_USER') != null) {
        return System.getenv('MAVEN_REPO_USER')
    }else {
        return project.findProperty('MAVEN_REPO_USER')
    }

    return ""
}

def getRepositoryPassword() {
    if(System.getenv('MAVEN_REPO_PASS') != null) {
        return System.getenv('MAVEN_REPO_PASS')
    }else {
        return project.findProperty('MAVEN_REPO_PASS')
    }

    return ""
}

configurations.all {
   resolutionStrategy {
   	cacheDynamicVersionsFor 60, 'seconds'
   }
}

dependencies {
    compile "fr.paladium:ymir:+:dev"
}
```

Déclarez ensuite la dépendance dans votre mod :

```java
@Mod(modid = Constants.MOD_ID, version = Constants.VERSION, dependencies = "required-after:ymir")
```

## Build

```sh
gradlew build         # Build le projet dans le dossier Ymir/build/libs.
```

## Publish

```sh
gradlew publish       # Met en ligne le projet sur repository.palagitium.dev
```
